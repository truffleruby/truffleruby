#!/usr/bin/env ruby

require 'json'

ruby_version = ENV['RUBY_VERSION'] or raise "RUBY_VERSION environment variable must be set"

root_dir = File.expand_path('..', __dir__)
ruby_src_dir = File.expand_path("../../ruby-#{ruby_version}", __dir__)

# Parse default gems from gemspec filenames
default_gems_dir = File.join(root_dir, 'lib/gems/specifications/default')
gemspecs = Dir.glob('*.gemspec', base: default_gems_dir)

default_gems = {}
gemspecs.each do |filename|
  basename = filename.sub(/\.gemspec$/, '')
  if basename =~ /^(.+)-(\d+\.\d+.*)$/
    default_gems[$1] = $2
  end
end

# Parse bundled gems from bundled_gems file
bundled_gems_file = File.join(ruby_src_dir, 'gems/bundled_gems')
bundled_gems = {}

File.readlines(bundled_gems_file).each do |line|
  line = line.strip
  next if line.empty? || line.start_with?('#')

  # Format: gem-name version repository-url [revision]
  parts = line.split
  bundled_gems[parts[0]] = parts[1]
end

# Extract RubyGems version from lib/rubygems.rb
rubygems_file = File.join(ruby_src_dir, 'lib/rubygems.rb')
rubygems_version = nil
File.readlines(rubygems_file).each do |line|
  if line =~ /^\s*VERSION\s*=\s*"([^"]+)"/
    rubygems_version = $1
    break
  end
end
raise "Could not find RubyGems VERSION in #{rubygems_file}" unless rubygems_version

# Build the versions structure with sorted keys
versions = {
  "rubygems" => rubygems_version,
  "gems" => {
    "default" => default_gems.sort.to_h,
    "bundled" => bundled_gems.sort.to_h
  }
}

# Write to versions.json
versions_file = File.join(root_dir, 'versions.json')
File.write(versions_file, JSON.pretty_generate(versions) + "\n")

puts "Updated #{versions_file}"
