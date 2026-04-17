# frozen_string_literal: true

require 'digest/sha2'
require 'open3'
require 'find'

module RBS
  module Collection
    module Sources
      class Git
        include Base
        METADATA_FILENAME = '.rbs_meta.yaml'

        class CommandError < StandardError; end

        attr_reader :name, :remote, :repo_dir, :revision

        def initialize(name:, revision:, remote:, repo_dir:)
          @name = name
          @remote = remote
          @repo_dir = repo_dir || 'gems'
          @revision = revision
          @need_setup = true
        end

        def has?(name, version)
          setup! do
            if version
              (gems_versions[name] || Set[]).include?(version)
            else
              gems_versions.key?(name)
            end
          end
        end

        def versions(name)
          setup! do
            versions = gems_versions[name] or raise "Git source `#{name}` doesn't have `#{name}`"
            versions.sort
          end
        end

        def install(dest:, name:, version:, stdout:)
          setup!()

          gem_dir = dest.join(name, version)

          colored_io = CLI::ColoredIO.new(stdout: stdout)

          case
          when gem_dir.symlink?
            colored_io.puts_green("Updating to #{format_config_entry(name, version)} from a local source")
            gem_dir.unlink
            _install(dest: dest, name: name, version: version)
          when gem_dir.directory?
            prev = load_metadata(dir: gem_dir)

            if prev == metadata_content(name: name, version: version)
              colored_io.puts "Using #{format_config_entry(name, version)}"
            else
              colored_io.puts_green("Updating to #{format_config_entry(name, version)} from #{format_config_entry(prev["name"], prev["version"])}")
              FileUtils.remove_entry_secure(gem_dir.to_s)
              _install(dest: dest, name: name, version: version)
            end
          when !gem_dir.exist?
            colored_io.puts_green("Installing #{format_config_entry(name, version)}")
            _install(dest: dest, name: name, version: version)
          else
            raise
          end
        end

        def manifest_of(name, version)
          setup! do
            path = File.join(repo_dir, name, version, 'manifest.yaml')
            content = git('cat-file', '-p', "#{resolved_revision}:#{path}")
            YAML.safe_load(content)
          rescue CommandError
            if has?(name, version)
              nil
            else
              raise
            end
          end
        end

        private def _install(dest:, name:, version:)
          # Should checkout that revision to support symlinks
          git("reset", "--hard", resolved_revision)

          dir = dest.join(name, version)
          dir.mkpath
          src = gem_repo_dir.join(name, version)

          cp_r(src, dir)
          write_metadata(dir: dir, name: name, version: version)
        end

        private def cp_r(src, dest)
          Find.find(src) do |file_src|
            file_src = Pathname(file_src)

            # Skip file if it starts with _, such as _test/
            Find.prune if file_src.basename.to_s.start_with?('_')

            file_src_relative = file_src.relative_path_from(src)
            file_dest = dest.join(file_src_relative)
            file_dest.dirname.mkpath
            FileUtils.copy_entry(file_src, file_dest, false, true) unless file_src.directory?
          end
        end

        def to_lockfile
          {
            'type' => 'git',
            'name' => name,
            'revision' => resolved_revision,
            'remote' => remote,
            'repo_dir' => repo_dir,
          }
        end

        private def format_config_entry(name, version)
          rev = resolved_revision[0..10]
          desc = "#{name}@#{rev}"

          "#{name}:#{version} (#{desc})"
        end

        private def setup!
          if @need_setup
            git_dir.mkpath
            if git_dir.join('.git').directory?
              if need_to_fetch?(revision)
                git 'fetch', 'origin'
              end
            else
              begin
                # git v2.27.0 or greater
                git 'clone', '--filter=blob:none', remote, git_dir.to_s, chdir: nil
              rescue CommandError
                git 'clone', remote, git_dir.to_s, chdir: nil
              end
            end

            @need_setup = false
          end

          yield if block_given?
        end

        private def need_to_fetch?(revision)
          return true unless commit_hash?

          !git?('cat-file', '-e', revision)
        end

        private def git_dir
          @git_dir ||= (
            base = Pathname(ENV['XDG_CACHE_HOME'] || File.expand_path("~/.cache"))
            cache_key = remote.start_with?('.') ? "#{remote}\0#{Dir.pwd}" : remote
            dir = base.join('rbs', Digest::SHA256.hexdigest(cache_key))
            dir.mkpath
            dir
          )
        end

        private def gem_repo_dir
          git_dir.join @repo_dir
        end

        def resolved_revision
          @resolved_revision ||=
            begin
              if commit_hash?
                revision
              else
                setup! { git('rev-parse', "refs/remotes/origin/#{revision}").chomp }
              end
            end
        end

        private def commit_hash?
          revision.match?(/\A[a-f0-9]{40}\z/)
        end

        private def git(*cmd, **opt)
          sh! 'git', *cmd, **opt
        end

        private def git?(*cmd, **opt)
          git(*cmd, **opt)
        rescue CommandError
          nil
        end

        private def sh!(*cmd, **opt)
          RBS.logger.debug "$ #{cmd.join(' ')}"
          opt = { chdir: git_dir }.merge(opt).compact
          (__skip__ = Open3.capture3(*cmd, **opt)).then do |out, err, status|
            raise CommandError, "Unexpected status #{status.exitstatus}\n\n#{err}" unless status.success?

            out
          end
        end

        def metadata_content(name:, version:)
          {
            "name" => name,
            "version" => version,
            "source" => to_lockfile
          }
        end

        def write_metadata(dir:, name:, version:)
          dir.join(METADATA_FILENAME).write(
            YAML.dump(
              metadata_content(name: name, version: version)
            )
          )
        end

        def load_metadata(dir:)
          # @type var content: Hash[String, untyped]
          content = YAML.load_file(dir.join(METADATA_FILENAME).to_s)
          _ = content.slice("name", "version", "source")
        end

        private def gems_versions
          @gems_versions ||= begin
            repo_path = Pathname(repo_dir)

            paths = git('ls-tree', '--full-tree', '-dr', '--name-only', '-z', resolved_revision, File.join(repo_dir, "")).split("\0").map {|line| Pathname(line) }

            # @type var versions: Hash[String, Set[String]]
            versions = {}

            paths.each do |full_path|
              path = full_path.relative_path_from(repo_path)

              gem_name, version = path.descend.take(2)

              if gem_name
                versions[gem_name.to_s] ||= Set[]

                if version && !version.basename.to_s.start_with?('_')
                  versions[gem_name.to_s] << version.basename.to_s
                end
              end
            end

            versions
          end
        end
      end
    end
  end
end
