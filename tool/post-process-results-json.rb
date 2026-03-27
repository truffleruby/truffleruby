#!/usr/bin/env ruby
# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'json'

result = JSON.parse(File.read(ARGV[0]))

result['queries'].each do |q|
  q.delete_if do |k, v|
    k =~ /^extra\.[\w-]+\.commit\.[\w-]+$/ || k =~ /^truffleruby\.commit\./
  end
end

File.write(ARGV[1], JSON.pretty_generate(result))
