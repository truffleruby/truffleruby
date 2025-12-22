# frozen_string_literal: true

module RBS
  module Collection

    # This class represent the configuration file.
    class Config
      class CollectionNotAvailable < StandardError
        def initialize
          super <<~MSG
            rbs collection is not initialized.
            Run `rbs collection install` to install RBSs from collection.
          MSG
        end
      end

      PATH = Pathname('rbs_collection.yaml')

      attr_reader :config_path, :data

      def self.find_config_path
        current = Pathname.pwd

        loop do
          config_path = current.join(PATH)
          return config_path if config_path.exist?
          current = current.join('..')
          return nil if current.root?
        end
      end

      # Generate a rbs lockfile from Gemfile.lock to `config_path`.
      # If `with_lockfile` is true, it respects existing rbs lockfile.
      def self.generate_lockfile(config_path:, definition:, with_lockfile: true)
        config = from_path(config_path)
        lockfile = LockfileGenerator.generate(config: config, definition: definition, with_lockfile: with_lockfile)

        [config, lockfile]
      end

      def self.from_path(path)
        new(YAML.load(path.read), config_path: path)
      end

      def self.to_lockfile_path(config_path)
        config_path.sub_ext('.lock' + config_path.extname)
      end

      def initialize(data, config_path:)
        @data = data
        @config_path = config_path
      end

      def gem(gem_name)
        gems.find { |gem| gem['name'] == gem_name }
      end

      def repo_path
        @config_path.dirname.join repo_path_data
      end

      def repo_path_data
        Pathname(@data["path"])
      end

      def sources
        @sources ||= [
          Sources::Rubygems.instance,
          *@data['sources'].map { |c| Sources.from_config_entry(c, base_directory: @config_path.dirname) },
          Sources::Stdlib.instance
        ]
      end

      def gems
        @data['gems'] ||= (
          [] #: Array[gem_entry]
        )
      end
    end
  end
end
