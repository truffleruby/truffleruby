# frozen_string_literal: true

module RBS
  module Collection
    class Config
      class LockfileGenerator
        ALUMNI_STDLIBS = { "mutex_m" => ">= 0.3.0" }

        class GemfileLockMismatchError < StandardError
          def initialize(expected:, actual:)
            @expected = expected
            @actual = actual
          end

          def message
            <<~MESSAGE
              RBS Collection loads a different Gemfile.lock from before.
              The Gemfile.lock must be the same as that is recorded in rbs_collection.lock.yaml.
              Expected Gemfile.lock: #{@expected}
              Actual Gemfile.lock: #{@actual}
            MESSAGE
          end
        end

        attr_reader :config, :lockfile, :definition, :existing_lockfile, :gem_hash, :gem_entries

        def self.generate(config:, definition:, with_lockfile: true)
          generator = new(config: config, definition: definition, with_lockfile: with_lockfile)
          generator.generate
          generator.lockfile
        end

        def initialize(config:, definition:, with_lockfile:)
          @config = config

          @gem_entries = config.gems.each.with_object({}) do |entry, hash| #$ Hash[String, gem_entry?]
            name = entry["name"]
            hash[name] = entry
          end

          lockfile_path = Config.to_lockfile_path(config.config_path)
          lockfile_dir = lockfile_path.parent

          @lockfile = Lockfile.new(
            lockfile_path: lockfile_path,
            path: config.repo_path_data,
            gemfile_lock_path: definition.lockfile.relative_path_from(lockfile_dir)
          )

          if with_lockfile && lockfile_path.file?
            @existing_lockfile = Lockfile.from_lockfile(lockfile_path: lockfile_path, data: YAML.load_file(lockfile_path.to_s))
            validate_gemfile_lock_path!(lock: @existing_lockfile, gemfile_lock_path: definition.lockfile)
          end

          @definition = definition
          @gem_hash = definition.locked_gems.specs.each.with_object({}) do |spec, hash|  #$ Hash[String, Bundler::LazySpecification]
            hash[spec.name] = spec
          end
        end

        def generate
          config.gems.each do |gem|
            case
            when gem.dig("source", "type") == "stdlib"
              unless gem.fetch("ignore", false)
                assign_stdlib(name: gem["name"])
              end
            else
              assign_gem(name: gem["name"], version: gem["version"])
            end
          end

          definition.dependencies.each do |dep|
            if dep.autorequire && dep.autorequire.empty?
              next
            end

            if spec = gem_hash[dep.name]
              assign_gem(name: dep.name, version: spec.version, skip: dep.source.is_a?(Bundler::Source::Gemspec))
            end
          end

          lockfile.lockfile_path.write(YAML.dump(lockfile.to_lockfile))
        end

        private def validate_gemfile_lock_path!(lock:, gemfile_lock_path:)
          return unless lock
          return unless lock.gemfile_lock_fullpath
          unless File.realpath(lock.gemfile_lock_fullpath) == File.realpath(gemfile_lock_path)
            raise GemfileLockMismatchError.new(expected: lock.gemfile_lock_fullpath, actual: gemfile_lock_path)
          end
        end

        private def assign_gem(name:, version:, skip: false)
          entry = gem_entries[name]
          src_data = entry&.fetch("source", nil)
          ignored = entry&.fetch("ignore", false)

          return if ignored
          return if lockfile.gems.key?(name)

          unless skip
            # @type var locked: Lockfile::library?

            if existing_lockfile
              locked = existing_lockfile.gems[name]
            end

            # If rbs_collection.lock.yaml contain the gem, use it.
            # Else find the gem from gem_collection.
            unless locked
              source =
                if src_data
                  Sources.from_config_entry(src_data, base_directory: config.config_path.dirname)
                else
                  find_source(name: name)
                end

              if source.is_a?(Sources::Stdlib)
                assign_stdlib(name: name)
                return
              end

              if source
                installed_version = version
                best_version = find_best_version(version: installed_version, versions: source.versions(name))

                locked = {
                  name: name,
                  version: best_version.to_s,
                  source: source,
                }
              end
            end

            if locked
              lockfile.gems[name] = locked

              begin
                locked[:source].dependencies_of(locked[:name], locked[:version])&.each do |dep|
                  assign_stdlib(name: dep["name"], from_gem: name)
                end
              rescue
                RBS.logger.warn "Cannot find `#{locked[:name]}-#{locked[:version]}` gem. Using incorrect Bundler context? (#{definition.lockfile})"
              end
            end
          end

          if spec = gem_hash.fetch(name, nil)
            spec.dependencies.each do |dep|
              if dep_spec = gem_hash[dep.name]
                assign_gem(name: dep.name, version: dep_spec.version)
              end
            end
          else
            RBS.logger.warn "Cannot find `#{name}` gem. Using incorrect Bundler context? (#{definition.lockfile})"
          end
        end

        private def assign_stdlib(name:, from_gem: nil)
          return if lockfile.gems.key?(name)

          case name
          when 'rubygems', 'set'
            msg = "`#{name}` has been moved to core library, so it is always loaded. Remove explicit loading `#{name}`"
            msg << " from `#{from_gem}`" if from_gem
            msg << "."
            return
          when *ALUMNI_STDLIBS.keys
            version = ALUMNI_STDLIBS.fetch(name)
            if from_gem
              # From `dependencies:` of a `manifest.yaml` of a gem
              source = find_source(name: name) or raise
              if source.is_a?(Sources::Stdlib)
                RBS.logger.warn {
                  "`#{name}` is included in the RBS dependencies of `#{from_gem}`, but the type definition as a stdlib in rbs-gem is deprecated. Add `#{name}` (#{version}) to the dependency of your Ruby program to use the gem-bundled type definition."
                }
              else
                RBS.logger.info {
                  "`#{name}` is included in the RBS dependencies of `#{from_gem}`, but the type definition as a stdlib in rbs-gem is deprecated. Delete `#{name}` from the RBS dependencies of `#{from_gem}`."
                }
                assign_gem(name: name, version: nil)
                return
              end
            else
              # From `gems:` of a `rbs_collection.yaml`
              RBS.logger.warn {
                "`#{name}` as a stdlib in rbs-gem is deprecated. Add `#{name}` (#{version}) to the dependency of your Ruby program to use the gem-bundled type definition."
              }
            end
          end

          source = Sources::Stdlib.instance
          lockfile.gems[name] = { name: name, version: "0", source: source }

          unless source.has?(name, nil)
            raise "Cannot find `#{name}` from standard libraries"
          end

          if deps = source.dependencies_of(name, "0")
            deps.each do |dep|
              assign_stdlib(name: dep["name"], from_gem: name)
            end
          end
        end

        private def find_source(name:)
          sources = config.sources

          sources.find { |c| c.has?(name, nil) }
        end

        private def find_best_version(version:, versions:)
          candidates = versions.map { |v| Gem::Version.create(v) or raise }
          return candidates.max || raise unless version

          v = Gem::Version.create(version) or raise
          Repository.find_best_version(v, candidates)
        end
      end
    end
  end
end
