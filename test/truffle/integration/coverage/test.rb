# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'coverage'

Coverage.start

require_relative 'subject.rb'

result = Coverage.result
key = result.keys.find { |k| k.end_with?('subject.rb') }
data = result[key]
expected = [
  nil,
  nil,
  nil,
  nil,
  nil,
  nil,
  nil,
  nil,
  1,  # a = 1
  1,  # b = 2
  nil,
  1,  # 10.times do
  10, #   c = 3
  nil,# end
  nil,
  1,  # d = 4
  nil,
  1,  # if d == 4
  1,  #   e = 5
  nil,# end
  nil,
  1,  # def foo
  2,  #   f = 6
  nil,# end
  nil,
  1,  # foo
  1,  # foo
  nil,
  1   # g = 7
]

raise "coverage data:\n#{data}\nnot as expected:\n#{expected}" unless data == expected
