# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module TruffleRuby
  class ConcurrentMap
    def initialize(initial_capacity: nil, load_factor: nil)
      Primitive.concurrent_map_initialize(self, initial_capacity || 0, load_factor || 0.0)
    end
  end
end
