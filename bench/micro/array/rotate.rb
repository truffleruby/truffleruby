# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

tiny = [0, 1]

benchmark 'core-array-rotate-inplace-tiny' do
  tiny.rotate!
end

small = 16.times.to_a

benchmark 'core-array-rotate-inplace-small' do
  small.rotate!
end

big = 1024.times.to_a

benchmark 'core-array-rotate-inplace-big' do
  big.rotate!
end

benchmark 'core-array-rotate-inplace-small-by-8' do
  small.rotate!(8)
end

benchmark 'core-array-rotate-inplace-big-by-512' do
  big.rotate!(512)
end
