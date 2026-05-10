# frozen_string_literal: true

require_relative './sources/base'
require_relative './sources/git'
require_relative './sources/stdlib'
require_relative './sources/rubygems'
require_relative './sources/local'

module RBS
  module Collection
    module Sources
      def self.from_config_entry(source_entry, base_directory:)
        case source_entry['type']
        when 'git', nil # git source by default
          # @type var source_entry: Git::source_entry
          Git.new(
            name: source_entry["name"],
            revision: source_entry["revision"],
            remote: source_entry["remote"],
            repo_dir: source_entry["repo_dir"]
          )
        when 'local'
          # @type var source_entry: Local::source_entry
          Local.new(
            path: source_entry['path'],
            base_directory: base_directory,
          )
        when 'stdlib'
          Stdlib.instance
        when 'rubygems'
          Rubygems.instance
        else
          raise
        end
      end
    end
  end
end
