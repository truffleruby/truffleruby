# frozen_string_literal: true

module RBS
  module Collection
    module Sources
      class Local
        include Base

        attr_reader :path, :full_path

        def initialize(path:, base_directory:)
          # TODO: resolve relative path from dir of rbs_collection.yaml
          @path = Pathname(path)
          @full_path = base_directory / path
        end

        def has?(name, version)
          if version
            @full_path.join(name, version).directory?
          else
            not versions(name).empty?
          end
        end

        def versions(name)
          @full_path.join(name).glob('*/').map { |path| path.basename.to_s }
        end

        # Create a symlink instead of copying file to refer files in @path.
        # By avoiding copying RBS files, the users do not need re-run `rbs collection install`
        # when the RBS files are updated.
        def install(dest:, name:, version:, stdout:)
          from = @full_path.join(name, version)
          gem_dir = dest.join(name, version)

          colored_io = CLI::ColoredIO.new(stdout: stdout)

          case
          when gem_dir.symlink? && gem_dir.readlink == from
            colored_io.puts "Using #{name}:#{version} (#{from})"
          when gem_dir.symlink?
            prev = gem_dir.readlink
            gem_dir.unlink
            _install(from, dest.join(name, version))
            colored_io.puts_green("Updating #{name}:#{version} to #{from} from #{prev}")
          when gem_dir.directory?
            # TODO: Show version of git source
            FileUtils.remove_entry_secure(gem_dir.to_s)
            _install(from, dest.join(name, version))
            colored_io.puts_green("Updating #{name}:#{version} from git source")
          when !gem_dir.exist?
            _install(from, dest.join(name, version))
            colored_io.puts_green("Installing #{name}:#{version} (#{from})")
          else
            raise
          end
        end

        private def _install(src, dst)
          dst.dirname.mkpath
          File.symlink(src, dst)
        end

        def manifest_of(name, version)
          gem_dir = @full_path.join(name, version)
          raise unless gem_dir.exist?

          manifest_path = gem_dir.join('manifest.yaml')
          YAML.safe_load(manifest_path.read) if manifest_path.exist?
        end

        def to_lockfile
          {
            'type' => 'local',
            'path' => @path.to_s,
          }
        end
      end
    end
  end
end
