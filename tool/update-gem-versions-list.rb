#!/usr/bin/env ruby
# frozen_string_literal: true

require 'json'

# Refactored: Removed dependency on RUBY_VERSION, relying solely on explicit RUBY_SOURCE_DIR.
class GemVersionManifestGenerator
  EXCLUDED_GEMS = %w[win32-registry win32ole].freeze
  RUBYGEMS_PATH = 'lib/rubygems.rb'
  BUNDLED_GEMS_PATH = 'gems/bundled_gems'

  def initialize
    @root_dir = File.expand_path('..', __dir__)
    @ruby_source_dir = ENV['RUBY_SOURCE_DIR']

    validate_source_directory!

    puts "Ruby source directory: #{@ruby_source_dir}"
  end

  def generate
    puts "Extracting default gems..."
    default_gems = extract_default_gems

    puts "Reading bundled gems..."
    bundled_gems = parse_bundled_gems

    puts "Reading RubyGems version..."
    rubygems_version = extract_rubygems_version

    versions = {
      "rubygems" => rubygems_version,
      "gems" => {
        "default" => default_gems.sort.to_h,
        "bundled" => bundled_gems.sort.to_h
      }
    }

    save_versions(versions)
    print_summary(rubygems_version, default_gems, bundled_gems)
  end

  private

  def validate_source_directory!
    if @ruby_source_dir.nil? || @ruby_source_dir.empty?
      raise "RUBY_SOURCE_DIR environment variable must be set."
    end

    unless File.directory?(@ruby_source_dir)
      raise "Ruby source directory not found at #{@ruby_source_dir}."
    end
  end

  def extract_default_gems
    default_gems = {}
    Dir.chdir(@ruby_source_dir) do
      # Script to extract default gems metadata using the source's own specifications
      script = <<~RUBY
        require 'rubygems/specification'
        gems = []
        Dir.glob('{lib,ext}/**/*.gemspec').each do |spec_path|
          spec = Gem::Specification.load(spec_path)
          gems << [spec.name, spec.version.to_s] if spec
        end
        gems.sort_by { |name, _| name }.each { |name, version| puts name + ' ' + version }
      RUBY

      output = `ruby -e "#{script}"`

      output.each_line do |line|
        name, version = line.strip.split(' ', 2)
        next if EXCLUDED_GEMS.include?(name)
        default_gems[name] = version if name && version
      end
    end
    default_gems
  end

  def parse_bundled_gems
    content = File.read(File.join(@ruby_source_dir, BUNDLED_GEMS_PATH))
    bundled_gems = {}
    content.each_line do |line|
      line = line.strip
      next if line.empty? || line.start_with?('#')
      # Format: gem-name version repository-url [revision]
      parts = line.split
      bundled_gems[parts[0]] = parts[1]
    end
    bundled_gems
  end

  def extract_rubygems_version
    content = File.read(File.join(@ruby_source_dir, RUBYGEMS_PATH))
    if content =~ /^\s*VERSION\s*=\s*"([^"]+)"/
      $1
    else
      raise "Could not find RubyGems VERSION in #{RUBYGEMS_PATH}"
    end
  end

  def save_versions(versions)
    versions_file = File.join(@root_dir, 'versions.json')
    File.write(versions_file, JSON.pretty_generate(versions) + "\n")
    puts "\nUpdated #{versions_file}"
  end

  def print_summary(rubygems_version, default_gems, bundled_gems)
    puts "- RubyGems version: #{rubygems_version}"
    puts "- Default gems: #{default_gems.size}"
    puts "- Bundled gems: #{bundled_gems.size}"
  end
end

if __FILE__ == $PROGRAM_NAME
  GemVersionManifestGenerator.new.generate
end
