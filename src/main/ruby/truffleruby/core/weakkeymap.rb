# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module ObjectSpace
  # WeakKeyMap uses #hash and #eql? to compare keys, like Hash
  class WeakKeyMap
    def inspect
      "#{super[0...-1]} size=#{Primitive.weakkeymap_size(self)}>"
    end
  end
end
