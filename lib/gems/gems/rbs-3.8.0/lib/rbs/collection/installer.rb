# frozen_string_literal: true

module RBS
  module Collection
    class Installer
      attr_reader :lockfile
      attr_reader :stdout

      def initialize(lockfile_path:, stdout: $stdout)
        @lockfile = Config::Lockfile.from_lockfile(lockfile_path: lockfile_path, data: YAML.load_file(lockfile_path))
        @stdout = stdout
      end

      def install_from_lockfile
        install_to = lockfile.fullpath
        install_to.mkpath
        selected = lockfile.gems.select do |name, gem|
          gem[:source].has?(name, gem[:version])
        end
        selected.each_value do |gem|
          gem[:source].install(
            dest: install_to,
            name: gem[:name],
            version: gem[:version],
            stdout: stdout
          )
        end
        CLI::ColoredIO.new(stdout: stdout).puts_green("It's done! #{selected.size} gems' RBSs now installed.")
      end
    end
  end
end
