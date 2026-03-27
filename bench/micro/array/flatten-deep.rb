# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

array = []
current_array = array
100.times do
  next_array = []
  current_array.concat [0, 1, next_array, 3 , 4]
  current_array = next_array
end

benchmark 'core-array-flatten-deep' do
  array.flatten
end
