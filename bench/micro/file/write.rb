# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

null = File.open('/dev/null', 'w')

kilobyte = 'x' * 1024

if defined?(Truffle::Debug.flatten_string)
  kilobyte = Truffle::Debug.flatten_string(kilobyte)
end

benchmark 'core-write-kilobyte' do
  null.write kilobyte
end

gigabyte = 'x' * 1024 * 1024 * 1024

if defined?(Truffle::Debug.flatten_string)
  gigabyte = Truffle::Debug.flatten_string(gigabyte)
end

benchmark 'core-write-gigabyte' do
  null.write gigabyte
end

benchmark 'core-big-concat-and-write' do
  string = '<html>'
  string += '<ul>'
  
  10_000.times do |n|
    string += '<li>' + n.to_s + '</li>'
  end
  
  string += '</ul>'
  string += '</html>'
  null.write string
end

benchmark 'core-big-join-and-write' do
  strings = ['<html>']
  strings.push '<ul>'
  
  10_000.times do |n|
    strings.push '<li>'
    strings.push n.to_s
    strings.push '</li>'
  end
  
  strings.push '</ul>'
  strings.push '</html>'
  null.write strings.join
end
