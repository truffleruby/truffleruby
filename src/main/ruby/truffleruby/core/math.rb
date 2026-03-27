# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Math

  PI = 3.14159265358979323846
  E = 2.7182818284590452354

  module_function

  def hypot(a, b)
    if Truffle::Type.fits_into_long?(a) and Truffle::Type.fits_into_long?(b)
      # Much faster (~10x) than calling the Math.hypot() / hypot(3)
      Math.sqrt(a*a + b*b)
    else
      Primitive.math_hypot Truffle::Type.rb_num2dbl(a), Truffle::Type.rb_num2dbl(b)
    end
  end

  def frexp(x)
    result = Primitive.math_frexp(x)
    if !Primitive.undefined?(result)
      result
    else
      frexp Truffle::Type.coerce_to_float(x)
    end
  end

  def ldexp(fraction, exponent)
    result = Primitive.math_ldexp(fraction, exponent)
    if !Primitive.undefined?(result)
      result
    elsif Primitive.is_a?(exponent, Float) and exponent.nan?
      raise RangeError, 'float NaN out of range of integer'
    else
      ldexp(
        Truffle::Type.coerce_to_float(fraction),
        Primitive.convert_with_to_int(exponent))
    end
  end

end
