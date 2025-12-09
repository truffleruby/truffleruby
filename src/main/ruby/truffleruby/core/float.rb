# frozen_string_literal: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
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

# Copyright (c) 2007-2014, Evan Phoenix and contributors
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

class Float < Numeric

  # to match MRI binary representation
  NAN        = -(0.0 / 0.0)
  INFINITY   = 1.0 / 0.0
  EPSILON    = 2.2204460492503131e-16
  RADIX      = 2
  ROUNDS     = 1
  MIN        = 2.2250738585072014e-308
  MAX        = 1.7976931348623157e+308
  MIN_EXP    = -1021
  MAX_EXP    = 1024
  MIN_10_EXP = -307
  MAX_10_EXP = 308
  DIG        = 15
  MANT_DIG   = 53

  def equal_fallback(other)
    # Fallback from Rubinius' Float#==, after the primitive call

    begin
      b, a = math_coerce(other)
      a == b
    rescue TypeError
      other == self
    end
  end
  private :equal_fallback

  def numerator
    if nan?
      NAN
    elsif infinite? == 1
      INFINITY
    elsif infinite? == -1
      -INFINITY
    else
      super
    end
  end

  def denominator
    if infinite? || nan?
      1
    else
      super
    end
  end

  def to_r
    f, e = Math.frexp self
    f = Math.ldexp(f, MANT_DIG).to_i
    e -= MANT_DIG

    (f * (RADIX ** e)).to_r
  end

  def arg
    if nan?
      self
    elsif self < 0 || Primitive.equal?(self, -0.0)
      Math::PI
    else
      0
    end
  end
  alias_method :angle, :arg
  alias_method :phase, :arg

  def rationalize(eps = undefined)
    if Primitive.undefined?(eps)
      f, n = Math.frexp self
      f = Math.ldexp(f, Float::MANT_DIG).to_i
      n -= Float::MANT_DIG

      Rational.__send__(:new_already_canonical, 2 * f, 1 << (1 - n)).rationalize(Rational.__send__(:new_already_canonical, 1, 1 << (1 - n)))
    else
      to_r.rationalize(eps)
    end
  end

  def ceil(ndigits = undefined)
    if Primitive.undefined?(ndigits)
      Primitive.float_ceil(self)
    else
      ndigits = Primitive.rb_num2int(ndigits)
      return ndigits > 0 ? 0.0 : 0 if self == 0.0
      if ndigits == 0
        Primitive.float_ceil(self)
      elsif ndigits > 0
        _, exp = Math.frexp(self)
        if ndigits >= (Float::DIG + 2) - (exp > 0 ? exp / 4 : exp / 3 - 1)
          # float is too large to be represent by ndigits, return self
          self
        elsif self < 0.0 && ndigits < -(exp > 0 ? exp / 3 + 1 : exp / 4)
          # float is too small to be represent by ndigits, return 0.0
          0.0
        else
          Primitive.float_ceil_ndigits(self, ndigits)
        end
      else
        ceiled = Primitive.float_ceil(self)
        ceiled.ceil(ndigits)
      end
    end
  end

  def floor(ndigits = undefined)
    if Primitive.undefined?(ndigits)
      Primitive.float_floor(self)
    else
      ndigits = Primitive.rb_num2int(ndigits)
      return ndigits > 0 ? 0.0 : 0 if self == 0.0
      if ndigits == 0
        Primitive.float_floor(self)
      elsif ndigits > 0
        _, exp = Math.frexp(self)
        if ndigits >= (Float::DIG + 2) - (exp > 0 ? exp / 4 : exp / 3 - 1)
          # float is too large to be represent by ndigits, return self
          self
        elsif self > 0.0 && ndigits < -(exp > 0 ? exp / 3 + 1 : exp / 4)
          # float is too small to be represent by ndigits, return 0.0
          0.0
        else
          Primitive.float_floor_ndigits(self, ndigits)
        end
      else
        floored = Primitive.float_floor(self)
        floored.floor(ndigits)
      end
    end
  end

  def round(ndigits = 0, half: :up)
    ndigits = Truffle::Type.coerce_to(ndigits, Integer, :to_int)

    if self == 0.0
      return ndigits > 0 ? self : 0
    end

    half = :up if Primitive.nil?(half)
    if half != :up && half != :down && half != :even
      raise ArgumentError, "invalid rounding mode: #{half}"
    end

    if ndigits <= 0
      if self.infinite?
        raise FloatDomainError, 'Infinite'
      elsif self.nan?
        raise FloatDomainError, 'NaN'
      end
    end

    if ndigits < 0
      to_i.round(ndigits, half: half)
    elsif ndigits == 0
      Truffle::FloatOperations.round_to_n_place(self, ndigits, half)
    elsif !infinite? && !nan?
      exponent = Primitive.float_exp(self)

      if Truffle::FloatOperations.round_overflow?(ndigits, exponent)
        self
      elsif Truffle::FloatOperations.round_overflow?(ndigits, exponent)
        0.0
      elsif ndigits > 14
        to_r.round(ndigits, half: half).to_f
      else
        Truffle::FloatOperations.round_to_n_place(self, ndigits, half)
      end
    else
      self # Infinity or NaN
    end
  end

  def truncate(ndigits = 0)
    if positive?
      floor(ndigits)
    else
      ceil(ndigits)
    end
  end

  def coerce(other)
    other = Float(other) unless Primitive.is_a?(other, Float)
    [other, self]
  end

  alias_method :quo, :/
  alias_method :fdiv, :/

  alias_method :modulo, :%

  def finite?
    not (nan? or infinite?)
  end

  class << self
    undef_method :new
  end

  def self.__allocate__
    raise TypeError, "allocator undefined for #{self}"
  end
end
