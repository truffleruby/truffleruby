# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Benchmarks the scenario where an empty hash is promoted to a packed hash.
hash = nil
benchmark 'core-hash-promote-null-hash' do
  hash = {}
  hash[:key] = :value
end
