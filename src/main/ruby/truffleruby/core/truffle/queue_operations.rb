# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module QueueOperations
    def self.validate_and_prepare_timeout_in_milliseconds(non_block, timeout_seconds_or_nil)
      return nil if Primitive.nil?(timeout_seconds_or_nil)
      raise ArgumentError, "can't set a timeout if non_block is enabled" if non_block

      (Truffle::Type.rb_num2dbl(timeout_seconds_or_nil) * 1000).to_i
    end
  end
end
