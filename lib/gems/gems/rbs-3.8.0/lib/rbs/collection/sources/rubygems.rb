# frozen_string_literal: true

require 'singleton'

module RBS
  module Collection
    module Sources
      # Signatures that are inclduded in gem package as sig/ directory.
      class Rubygems
        include Base
        include Singleton

        def has?(name, version)
          gem_sig_path(name, version)
        end

        def versions(name)
          spec, _ = gem_sig_path(name, nil)
          spec or raise
          [spec.version.to_s]
        end

        def install(dest:, name:, version:, stdout:)
          # Do nothing because stdlib RBS is available by default
          _, from = gem_sig_path(name, version)
          stdout.puts "Using #{name}:#{version} (#{from})"
        end

        def manifest_of(name, version)
          _, sig_path = gem_sig_path(name, version)
          sig_path or raise
          manifest_path = sig_path.join('manifest.yaml')
          YAML.safe_load(manifest_path.read) if manifest_path.exist?
        end

        def to_lockfile
          {
            'type' => 'rubygems',
          }
        end

        private def gem_sig_path(name, version)
          RBS::EnvironmentLoader.gem_sig_path(name, version)
        end
      end
    end
  end
end
