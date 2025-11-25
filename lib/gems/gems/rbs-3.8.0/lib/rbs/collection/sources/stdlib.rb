# frozen_string_literal: true

require 'singleton'

module RBS
  module Collection
    module Sources
      # signatures that are bundled in rbs gem under the stdlib/ directory
      class Stdlib
        include Base
        include Singleton

        REPO = Repository.default

        def has?(name, version)
          lookup(name, version)
        end

        def versions(name)
          REPO.gems[name].versions.keys.map(&:to_s)
        end

        def install(dest:, name:, version:, stdout:)
          # Do nothing because stdlib RBS is available by default
          from = lookup(name, version)
          stdout.puts "Using #{name}:#{version} (#{from})"
        end

        def manifest_of(name, version)
          unless path = lookup(name, version)
            RBS.logger.warn "`#{name}` is specified in rbs_collection.lock.yaml. But it is not found in #{REPO.dirs.join(",")}"
            return
          end
          manifest_path = path.join('manifest.yaml')
          YAML.safe_load(manifest_path.read) if manifest_path.exist?
        end

        def to_lockfile
          {
            'type' => 'stdlib',
          }
        end

        private def lookup(name, version)
          REPO.lookup(name, version)
        end
      end
    end
  end
end
