# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Benchmarks repeatedly reassigning various entries in a bucket hash.

hash = { 1 => 1, 2 => 2, 3 => 3, 4 => 4 } # big enough to start as a bucket hash
i = 0
size = hash.size
benchmark 'core-hash-buckets-reassign' do
  hash[i += 1] = i
  i = 0 if i >= size
end
