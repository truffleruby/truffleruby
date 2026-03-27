# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Benchmarks repeatedly growing a bucket hash store.

max = 400_000 # > 0.75*(524288 + 21) (cf. BucketsHashStore)
hash = { a: 1, b: 2, c: 3, d: 4 } # big enough to start as a bucket hash
i = 0
benchmark 'core-hash-buckets-grow' do
  hash[i += 1] = i
  if i >= max
    hash = { a: 1, b: 2, c: 3, d: 4 }
    i = 0
  end
end
