# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

little_array_of_bytes = [19, 123, 43, 32, 94, 43, 28, 93, 39, 2]

benchmark 'core-array-pack-little-C' do
  little_array_of_bytes.pack('C*')
end

big_array_of_bytes = little_array_of_bytes * 1_000

benchmark 'core-array-pack-big-C' do
  big_array_of_bytes.pack('C*')
end
