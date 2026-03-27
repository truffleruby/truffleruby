#!/usr/bin/env ruby

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Usage
#   1. `jt ruby tool/compare-rbconfig.rb`
#   2. `jt -u ruby ruby tool/compare-rbconfig.rb`
#   3. Compare ruby.txt and truffleruby.txt in your diff tool.

require 'rbconfig'

File.open("#{RUBY_ENGINE}.txt", "w") do |f|
  f.puts RUBY_VERSION

  prefix = RbConfig::CONFIG['prefix']

  {
    # 'RbConfig::CONFIG' => RbConfig::CONFIG,
    'RbConfig::MAKEFILE_CONFIG' => RbConfig::MAKEFILE_CONFIG
  }.each do |name, h|
    f.puts
    f.puts name
    h.keys.sort.each { |k|
      value = h[k]

      value = value.gsub(prefix, '$(prefix)')

      f.puts "#{k} = #{value}"
    }
  end
end
