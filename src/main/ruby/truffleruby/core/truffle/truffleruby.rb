# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module TruffleRuby

  def self.revision
    ::RUBY_REVISION
  end

  class AtomicReference

    # Same logic as https://github.com/ruby-concurrency/concurrent-ruby/blob/4c8fc28ab6/lib/concurrent-ruby/concurrent/atomic_reference/numeric_cas_wrapper.rb
    # We handle the Numeric case here so TruffleRuby::AtomicReference can be used without needing concurrent-ruby
    def compare_and_set(expected_value, new_value)
      if Primitive.is_a?(expected_value, Numeric)
        # NaN is never == to itself, so match it explicitly
        expected_nan = Primitive.is_a?(expected_value, Float) && expected_value.nan?

        begin
          current_value = get
          return false unless Primitive.is_a?(current_value, Numeric)

          if expected_nan
            return false unless Primitive.is_a?(current_value, Float) && current_value.nan?
          else
            return false unless current_value == expected_value
          end
        end until compare_and_set_reference(current_value, new_value)
        true
      else
        compare_and_set_reference(expected_value, new_value)
      end
    end

    def marshal_dump
      get
    end

    def marshal_load(value)
      set value
    end

  end

end
