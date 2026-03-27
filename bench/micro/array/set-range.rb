# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

array = (0...1000).to_a
shift = 0

benchmark 'core-array-set-range' do
  i = 0
  replacement = (shift...(shift + 10)).to_a
  while i < 1000 - 10
    array[i...(i+10)] = replacement
    i += 1
  end
  shift += 1
  shift = 0 if shift > 1000
end