# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

module Truffle
  module RangeOperations
    def self.step_fallback(range, step_size, &block) # :yields: object
      return step_no_block(range, step_size) unless block

      first = range.begin
      last = range.end
      exclude_end = range.exclude_end?

      if Primitive.nil?(first)
        raise ArgumentError, '#step iteration for beginless ranges is meaningless'
      end

      # String/Symbol ranges with Integer steps retain pre-3.4 succ-based iteration for
      # backward compatibility. See https://bugs.ruby-lang.org/issues/18368 for discussion.
      if (Primitive.is_a?(first, String) || Primitive.is_a?(first, Symbol)) && Primitive.is_a?(step_size, Integer)
        check_step_zero(step_size)
        raise ArgumentError, "step can't be negative" if step_size < 0

        i = 0
        range.each do |item|
          yield item if i % step_size == 0
          i += 1
        end

        return range
      end

      # When any argument involves a Float, coerce all values to Float for consistent
      # floating-point iteration. This mirrors CRuby's ruby_float_step behavior.
      if Primitive.is_a?(first, Float) || Primitive.is_a?(last, Float) || Primitive.is_a?(step_size, Float)
        step_size = Truffle::Type.rb_num2dbl(step_size)
        check_step_zero(step_size)
        desc = step_size < 0
        first = Truffle::Type.rb_num2dbl(first)
        if Primitive.nil?(last)
          last = desc ? -Float::INFINITY : Float::INFINITY
        else
          last = Truffle::Type.rb_num2dbl(last)
        end

        Truffle::NumericOperations.step_float(first, last, step_size, step_size < 0, exclude_end, &block)
        return range
      end

      # Numeric + numeric step (non-Float)
      if Primitive.is_a?(first, Numeric) && Primitive.is_a?(step_size, Numeric)
        check_step_zero(step_size)
        if Primitive.nil?(last)
          curr = first
          while true
            yield curr
            curr += step_size
          end
        else
          desc = step_size < 0
          if exclude_end
            Truffle::NumericOperations.step_non_float_exclude_end(first, last, step_size, desc, &block)
          else
            Truffle::NumericOperations.step_non_float(first, last, step_size, desc, &block)
          end
        end

        return range
      end

      # Generic path: iterate using the + operator, which is the Ruby 3.4 behavior
      # that enables stepping through non-numeric ranges like Time and Date.
      # This does NOT raise for step_size == 0.
      if Primitive.nil?(last)
        curr = first
        while true
          yield curr
          curr += step_size
        end
      else
        direction = (first <=> last)

        if Primitive.nil?(direction)
          return range
        elsif direction == 0
          yield first unless exclude_end
          return range
        end

        # Verify the step moves iteration in the same direction as from begin to end;
        # otherwise, the iteration should be empty.
        second = first + step_size
        step_direction = (first <=> second)

        if Primitive.nil?(step_direction) || step_direction != direction
          return range
        end

        # Iterate while curr is on the same side of last as first was.
        # For forward ranges (direction=-1): continues while curr < last.
        # For backward ranges (direction=1): continues while curr > last.
        curr = first
        while (cmp = (curr <=> last)) == direction
          yield curr
          curr += step_size
        end
        yield curr if !exclude_end && cmp == 0
      end

      range
    end
    Primitive.always_split singleton_class, :step_fallback

    def self.step_no_block(range, step_size)
      from, to = range.begin, range.end

      if Primitive.is_a?(step_size, Numeric)
        check_step_zero(step_size) if Primitive.is_a?(from, Numeric)

        if arithmetic_range?(from, to)
          enum = Enumerator::ArithmeticSequence.allocate
          return enum.send(:initialize_internal, range, :step, from, to, step_size, range.exclude_end?)
        end
      end

      if Primitive.nil?(from)
        raise ArgumentError, '#step for non-numeric beginless ranges is meaningless'
      end

      range.to_enum(:step, step_size) { nil }
    end

    def self.check_step_zero(step_size)
      if step_size == 0
        raise ArgumentError, "step can't be 0"
      end
    end

    def self.arithmetic_range?(from, to)
      if Primitive.is_a?(from, Numeric)
        Primitive.is_a?(to, Numeric) || Primitive.nil?(to)
      else
        Primitive.nil?(from) && Primitive.is_a?(to, Numeric)
      end
    end

    # MRI: r_less
    def self.r_less(a, b)
      cmp = (a <=> b)
      return 1 if Primitive.nil?(cmp)
      Comparable.compare_int(cmp)
    end

    # MRI: r_cover_range_p
    def self.range_cover?(range, other)
      range_begin = range.begin
      range_end = range.end
      other_begin = other.begin
      other_end = other.end

      # finite end cannot cover endless end: (a..b).cover?(c..) is false
      # finite begin cannot cover beginless begin: (a..b).cover?(..c) is false
      return false if !Primitive.nil?(range_end) && Primitive.nil?(other_end)
      return false if !Primitive.nil?(range_begin) && Primitive.nil?(other_begin)

      # cannot cover an empty or backward range (e.g., 5...5 or 5..0)
      if !Primitive.nil?(other_begin) && !Primitive.nil?(other_end)
        if r_less(other_begin, other_end) > (other.exclude_end? ? -1 : 0)
          return false
        end
      end

      # must cover other's begin value
      if !Primitive.nil?(other_begin) && !cover?(range, other_begin)
        return false
      end

      # compare end values
      if !Primitive.nil?(other_end) && !Primitive.nil?(range_end)
        cmp_end = (range_end <=> other_end)
        return false if Primitive.nil?(cmp_end)
        cmp_end = Comparable.compare_int(cmp_end)
      else
        # end is nil, other.end is either nil or finite so could return immediately `true`,
        # but there is an edge cases with exclusive range and inclusive other,
        # e.g. (a...).cover?(a..) => false
        cmp_end = r_less(range_end, other_end)
      end

      if range.exclude_end? == other.exclude_end?
        return cmp_end >= 0
      elsif range.exclude_end?
        # exclusive range (a...b) covers inclusive (c..d) if end > other.end
        return cmp_end > 0
      elsif cmp_end >= 0
        # inclusive range (a..b) covers exclusive (c...d) if end >= other.end
        return true
      end

      # edge case: (a..b).cover?(c...d) where b < d;
      # compare range.end with other.max to handle non-numeric types.
      begin
        other_max = other.max
      rescue TypeError
        other_max = nil
      end
      return false if Primitive.nil?(other_max)

      r_less(range_end, other_max) >= 0
    end

    # MRI: r_cover_p
    def self.cover?(range, value)
      # Check lower bound
      if !Primitive.nil?(range.begin)
        cmp = (range.begin <=> value)
        return false if Primitive.nil?(cmp)
        return false if Comparable.compare_int(cmp) > 0
      end

      # Check upper bound
      if !Primitive.nil?(range.end)
        cmp = (value <=> range.end)
        return false if Primitive.nil?(cmp)

        if range.exclude_end?
          return false if Comparable.compare_int(cmp) >= 0
        else
          return false if Comparable.compare_int(cmp) > 0
        end
      end

      true
    end

    # MRI: linear_object_p
    def self.linear?(object)
      Primitive.is_a?(object, Numeric) || Primitive.is_a?(object, Time)
    end

    # MRI: empty_region_p
    def self.greater_than?(from, to, to_exclusive)
      return false if Primitive.nil?(from)
      return false if Primitive.nil?(to)

      cmp = from <=> to

      return true if Primitive.nil?(cmp)
      return true if cmp == 0 && to_exclusive

      cmp > 0 # that's from > to
    end
  end
end
