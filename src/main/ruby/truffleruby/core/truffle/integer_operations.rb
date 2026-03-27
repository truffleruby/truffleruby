# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module IntegerOperations
    def self.bits_reference_range(n, range)
      raise FloatDomainError , 'Infinity' if range.begin == Float::INFINITY || range.end == Float::INFINITY
      raise FloatDomainError , '-Infinity' if range.begin == -Float::INFINITY || range.end == -Float::INFINITY

      if !Primitive.nil?(range.begin) && !Primitive.nil?(range.end)
        len = range.end - range.begin
        len += 1 unless range.exclude_end?
        num = n >> range.begin
        mask = (1 << len) - 1

        range.end < range.begin ? num : num & mask
      elsif Primitive.nil?(range.end)
        n >> range.begin
      else
        len = range.end
        len += 1 unless range.exclude_end?
        mask = (1 << len) - 1

        if n & mask != 0 && range.end >= 0
          raise ArgumentError, 'The beginless range for Integer#[] results in infinity'
        end

        0
      end
    end

    # Implementation of a**b mod c operation
    # See https://en.wikipedia.org/wiki/Modular_exponentiation
    # The only difference with the Right-to-left binary method is a special handling of negative modulus -
    # a**b mod -c = (a**b mod c) - c
    # MRI: similar to int_pow_tmp1/int_pow_tmp2/int_pow_tmp3
    def self.modular_exponentiation(base, exponent, modulus)
      return 0 if modulus == 1

      negative = modulus < 0
      modulus = modulus.abs

      result = 1
      base %= modulus

      while exponent > 0
        if exponent.odd?
          result = (result * base) % modulus
        end

        base = (base * base) % modulus
        exponent >>= 1
      end

      result -= modulus if negative
      result
    end
    Primitive.always_split singleton_class, :modular_exponentiation
  end
end
