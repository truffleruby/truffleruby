# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'rbconfig'
require 'json'

gems = []

`#{RbConfig.ruby} -S gem list`.lines.map(&:chomp).reject { |line|
  line.empty? or line.include?('LOCAL GEMS')
}.each { |line|
  gem, versions = line.split(' (', 2)
  versions = versions.chomp(')')
  versions = versions.split(', ')

  # TODO: remove after upgrading Rubygems
  # See https://github.com/rubygems/rubygems/issues/8178
  versions = versions.map { |s| s.chomp(' ruby') }

  versions.each { |version|
    if version.include?('default: ')
      gems << [gem, version.sub('default: ', '')]
    end
  }
}

bundled_gems = JSON.load(File.read(File.expand_path('../' * 5 + 'versions.json', __FILE__ )))['gems']['bundled']
bundled_gems.delete 'typeprof'
gems += bundled_gems.to_a

File.write 'Gemfile', <<GEMFILE
source 'https://rubygems.org'

#{gems.map { |name, version| "gem #{name.inspect}, #{version.inspect}" }.join("\n")}
GEMFILE
