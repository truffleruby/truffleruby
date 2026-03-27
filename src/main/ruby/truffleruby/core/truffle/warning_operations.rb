# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module WarningOperations
    def self.check_category(category)
      return if category == :deprecated || category == :experimental || category == :performance

      raise ArgumentError, "unknown category: #{category}"
    end

    def self.performance_warning(message)
      ::Warning.warn(message, category: :performance)
    end
  end
end
