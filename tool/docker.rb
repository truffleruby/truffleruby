# frozen_string_literal: true

require 'yaml'

class JT
  class Docker
    include Utilities

    DOCKER = ENV['DOCKER'] || 'docker'

    def docker(*args)
      command = args.shift
      case command
      when 'build'
        docker_build(*args)
      when nil, 'test'
        docker_test(*args)
      when 'print'
        puts dockerfile(*args, '--print')
      else
        abort "Unkown jt docker command #{command}"
      end
    end

    private def docker_config
      @config ||= begin
        contents = File.read(File.join(TRUFFLERUBY_DIR, 'tool', 'docker-configs.yaml'))
        YAML.respond_to?(:unsafe_load) ? YAML.unsafe_load(contents) : YAML.load(contents)
      end
    end

    private def docker_distros
      docker_config.each_pair.select { |_name, details| details.key?('base') }.map(&:first).map { |distro| "--#{distro}" }
    end

    private def default_docker_dir
      @default_docker_dir ||= "#{TRUFFLERUBY_DIR}/tool/docker-pid#{Process.pid}"
    end

    private def docker_build(*args, docker_dir: default_docker_dir)
      if args.first.nil? || args.first.start_with?('--')
        image_name = 'truffleruby-test'
      else
        image_name = args.shift
      end
      File.write(File.join(docker_dir, 'Dockerfile'), dockerfile(*args, docker_dir: docker_dir))
      begin
        sh DOCKER, 'build', '-t', image_name, '.', chdir: docker_dir
      ensure
        FileUtils.rm_rf docker_dir
      end
    end

    private def docker_test(*args)
      distros = docker_distros
      if args.first == '--filter'
        args.shift
        filter = args.shift
        distros = distros.select { |distro| distro.include?(filter) }
      end

      distros.each do |distro|
        puts '**********************************'
        puts '**********************************'
        puts '**********************************'
        distros.each do |d|
          print d
          print '     <---' if d == distro
          puts
        end
        puts '**********************************'
        puts '**********************************'
        puts '**********************************'

        docker 'build', distro, *args
      end
    end

    private def dockerfile(*args, docker_dir: default_docker_dir)
      config = docker_config

      distro_name = 'ol8'
      install_method = nil
      rebuild_openssl = true
      basic_test = false
      full_test = false
      root = false
      print_only = false

      until args.empty?
        arg = args.shift
        case arg
        when *docker_distros
          distro_name = arg[2..-1]
        when '--standalone'
          install_method = :standalone
          standalone_tarball = args.shift
        when '--no-rebuild-openssl'
          rebuild_openssl = false
        when '--basic-test'
          basic_test = true
        when '--test'
          full_test = true
          test_branch = args.shift
        when '--root'
          root = true
        when '--print'
          print_only = true
          docker_dir = nil # Make sure it is not used
        else
          abort "unknown option #{arg}"
        end
      end

      distro = config.fetch(distro_name)
      run_post_install_hook = rebuild_openssl

      packages = []
      packages << distro.fetch('locale')

      packages << distro.fetch('tar')
      packages << distro.fetch('specs') if full_test

      packages << distro.fetch('zlib')
      packages << distro.fetch('openssl')
      packages << distro.fetch('cext')
      packages << distro.fetch('c++') if full_test

      proxy_vars = []
      # There is an issue with dnf + proxy in Fedora 34, install packages outside proxy to workaround
      unless distro_name == 'fedora34'
        %w[http_proxy https_proxy no_proxy].each do |var|
          value = ENV[var]
          proxy_vars << "ENV #{var}=#{value}" if value
        end
      end

      lines = [
        "FROM #{distro.fetch('base')}",
        *proxy_vars,
        [distro.fetch('install'), *packages.compact].join(' '),
        *distro.fetch('set-locale'),
      ]

      # Check the locale is properly generated
      lines << 'RUN locale -a | grep en_US.utf8'

      lines << 'WORKDIR /test'

      lines << 'RUN useradd -ms /bin/bash test'
      lines << 'RUN chown test /test'
      lines << 'USER test' unless root

      unless print_only
        FileUtils.rm_rf docker_dir
        Dir.mkdir docker_dir
      end

      case install_method
      when :standalone
        FileUtils.copy standalone_tarball, docker_dir unless print_only
        standalone_tarball = File.basename(standalone_tarball)
        lines << "COPY #{standalone_tarball} /test/"
        ruby_base = '/test/truffleruby-standalone'
        lines << "RUN mkdir #{ruby_base}"
        lines << "RUN tar -zxf #{standalone_tarball} -C #{ruby_base} --strip-components=1"
        ruby_bin = "#{ruby_base}/bin"
        lines << "RUN #{ruby_base}/lib/truffle/post_install_hook.sh" if run_post_install_hook
      else
        raise "Unknown install method: #{install_method}"
      end

      if full_test
        test_files = %w[
          spec
          versions.json
        ]

        unless print_only
          chdir(docker_dir) do
            branch_args = test_branch == 'current' ? [] : ['--branch', test_branch]
            sh 'git', 'clone', *branch_args, TRUFFLERUBY_DIR, 'truffleruby-tests'
            test_files.each do |file|
              dir = File.dirname(file)
              FileUtils.mkdir_p dir
              FileUtils.cp_r "truffleruby-tests/#{file}", dir
            end
            FileUtils.rm_rf 'truffleruby-tests'
          end
        end
      end

      lines << "ENV PATH=#{ruby_bin}:$PATH"

      lines << 'RUN ruby --version'

      if basic_test || full_test
        lines << "RUN cp -R #{ruby_base}/lib/gems /test/clean-gems"

        gem_install = 'ruby -S gem install --no-document'
        lines << "RUN #{gem_install} color"
        lines << "RUN ruby -rcolor -e 'raise unless defined?(Color)'"

        lines << "RUN #{gem_install} oily_png"
        lines << "RUN ruby -roily_png -e 'raise unless defined?(OilyPNG::Color)'"

        lines << "RUN #{gem_install} unf"
        lines << "RUN ruby -runf -e 'raise unless defined?(UNF)'"

        lines << "RUN rm -rf #{ruby_base}/lib/gems"
        lines << "RUN mv /test/clean-gems #{ruby_base}/lib/gems"
      end

      if full_test
        # lines << 'ENV TRUFFLERUBY_ALL_INTEROP_LIBRARY_METHODS_SPEC=false'
        test_files.each do |path|
          file = File.basename(path)
          lines << "COPY --chown=test #{file} #{file}"
        end

        excludes = %w[fails slow]

        %w[:command_line :security :language :core :tracepoint :library :capi :truffle :truffle_capi].each do |set|
          t_excludes = excludes.map { |e| '--excl-tag ' + e }.join(' ')
          lines << "RUN ruby spec/mspec/bin/mspec -t #{ruby_bin}/ruby #{t_excludes} #{set}"
        end
      end

      lines << 'CMD bash'

      lines.join("\n") + "\n"
    end
  end
end
