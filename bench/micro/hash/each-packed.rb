# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Benchmarks Hash#each for packed hashes.

if RUBY_ENGINE == 'truffleruby'
  hash = { a: 1, b: 2, c: 3 }
  benchmark 'core-hash-each-packed' do
    hash.each { |k, v| Primitive.blackhole(v) }
  end
end
