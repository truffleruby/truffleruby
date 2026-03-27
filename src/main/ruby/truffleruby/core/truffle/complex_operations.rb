# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module ComplexOperations
    def self.convert_not_real_arguments(real, imag, exception)
      raise_exception = !Primitive.false?(exception)
      if Primitive.nil?(real) || Primitive.nil?(imag)
        return nil unless raise_exception
        raise TypeError, "can't convert nil into Complex"
      end
      imag = nil if Primitive.undefined?(imag)

      if Primitive.is_a?(real, String)
        real = String::Complexifier.new(real).strict_convert(exception)
        return nil if Primitive.nil?(real)
      end

      if Primitive.is_a?(imag, String)
        imag = String::Complexifier.new(imag).strict_convert(exception)
        return nil if Primitive.nil?(imag)
      end

      if Primitive.is_a?(real, Complex) && !Primitive.is_a?(real.imag, Float) && real.imag == 0
        real = real.real
      end

      if Primitive.is_a?(imag, Complex) && !Primitive.is_a?(imag.imag, Float) && imag.imag == 0
        imag = imag.real
      end

      if Primitive.is_a?(real, Complex) && !Primitive.is_a?(imag, Float) && imag == 0
        return real
      end

      if Primitive.nil? imag
        if Primitive.is_a?(real, Numeric) && !real.real?
          return real
        elsif !Primitive.is_a?(real, Numeric)
          if raise_exception
            return Primitive.convert_type(real, Complex, :to_c)
          else
            return Truffle::Type.rb_check_convert_type(real, Complex, :to_c)
          end
        else
          imag = 0
        end
      elsif Primitive.is_a?(real, Numeric) && Primitive.is_a?(imag, Numeric) && (!real.real? || !imag.real?)
        return real + imag * Complex.new(0, 1)
      end

      if !Primitive.nil?(imag) && !raise_exception && !Primitive.is_a?(imag, Integer) &&
        !Primitive.is_a?(imag, Float) && !Primitive.is_a?(imag, Rational)
        return nil
      end

      Complex.rect(real, imag)
    end
  end
end
