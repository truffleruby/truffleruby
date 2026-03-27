# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Benchmarks looking up keys

if RUBY_ENGINE == 'truffleruby'
  max = 400_000 # > 0.75*(524288 + 21) (cf. BucketsHashStore)
  hash = { a: 1, b: 2, c: 3, d: 4 } # big enough to start as a bucket hash
  max.times { |i|
    hash[i] = i
  }

  benchmark 'core-hash-buckets-lookup' do
    1000.times do |i|
      Primitive.blackhole(hash[i])
    end
  end
end
