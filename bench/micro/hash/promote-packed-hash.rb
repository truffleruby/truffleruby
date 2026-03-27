# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Benchmarks the scenario where a packed hash (default limit = 3 items) is promoted to a bucket hash.
benchmark 'core-hash-promote-packed-hash' do
  hash = { a: 1, b: 2, c: 3 }
  hash[:key] = :value
end
