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
  module RandomOperations

    # MRI: rand_random
    def self.random(randomizer, limit, error)
      if Primitive.undefined?(limit)
        return randomizer.random_float
      end

      invalid_argument(limit) if Primitive.nil?(limit)

      unless Primitive.is_a?(limit, Float)
        if limit_int = Truffle::Type.rb_check_to_integer(limit, :to_int)
          return rand_int(randomizer, limit_int, true)
        end
      end

      limit_float = Truffle::Type.rb_check_to_float(limit)
      if !Primitive.nil?(limit_float)
        if limit_float < 0.0
          invalid_argument(limit)
        else
          check_float(limit_float)
          r = randomizer.random_float
          r *= limit_float if limit_float > 0.0
          r
        end
      elsif Primitive.is_a?(limit, Range)
        rand_range(randomizer, limit)
      else
        error == ArgumentError ? invalid_argument(limit) : Primitive.rb_num2long(limit)
      end
    end

    def self.check_float(value)
      raise Errno::EDOM if value.infinite? || value.nan?
      value
    end

    def self.invalid_argument(limit)
      raise ArgumentError, "invalid argument - negative limit: #{limit}"
    end

    def self.rand_int(randomizer, limit, restricted)
      invalid_argument(limit) if limit == 0
      if limit < 0
        invalid_argument(limit) if restricted
        limit = -limit
      end
      randomizer.random_integer(limit - 1)
    end

    def self.rand_range(randomizer, range)
      b, e, exclude_end = range.begin, range.end, range.exclude_end?
      raise Errno::EDOM if Primitive.nil?(b) || Primitive.nil?(e)

      diff = e - b
      if !Primitive.is_a?(diff, Float) &&
        !Primitive.nil?(v = Truffle::Type.rb_check_to_integer(diff, :to_int))
        max = exclude_end ? v - 1 : v
        if max >= 0
          v = randomizer.random_integer(max)
        else
          invalid_argument(range)
        end
      elsif !Primitive.nil?(v = Truffle::Type.rb_check_to_float(diff))
        scale = 1
        max = v
        mid = 0.5
        r = 0.0
        if v.infinite?
          min = check_float(Truffle::Type.rb_to_f(b)) / 2.0
          max = check_float(Truffle::Type.rb_to_f(e)) / 2.0
          scale = 2
          mid = max + min
          max -= min
        elsif v.nan?
          raise Errno::EDOM
        end

        if max > 0.0
          r = randomizer.random_float
          if scale > 1
            r *= 1.0000000000000002 unless exclude_end
            return +(+(+(r - 0.5) * max) * scale) + mid
          end
          v = r * max
        elsif max == 0.0 && !exclude_end
          v = 0.0
        else
          invalid_argument(range)
        end
      end

      if Primitive.is_a?(b, Integer) && Primitive.is_a?(v, Integer)
        return b + v
      end

      case v
      when Float
        f = Truffle::Type.rb_check_to_float(b)
        return v + f unless Primitive.nil?(f)
        b + v
      else
        b + v
      end
    end

    def self.obj_random_int(randomizer)
      obj_random_bytes(randomizer.value, 4).unpack1('l')
    end

    def self.obj_random_bytes(obj, len)
      bytes = obj.bytes(len)
      raise TypeError, 'type must be String' unless Primitive.is_a?(bytes, String)
      bytesize = bytes.bytesize
      unless bytesize == len
        raise RangeError, "random data too #{bytesize < len ? 'short' : 'long'} #{bytesize}"
      end
      bytes
    end
  end
end
