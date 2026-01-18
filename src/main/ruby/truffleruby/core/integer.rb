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

class Integer < Numeric

  # Have a copy in Integer of the Numeric version, as MRI does
  alias_method :remainder, :remainder

  def **(o)
    pow = Primitive.integer_pow self, o
    unless Primitive.undefined?(pow)
      return pow
    end

    if (Primitive.is_a?(o, Float) || Primitive.is_a?(o, Rational)) && self < 0 && o != o.round
      return Complex.new(self, 0) ** o
    elsif Primitive.is_a?(o, Integer) && o < 0
      return Rational.__send__(:new_already_canonical, self, 1) ** o
    elsif Primitive.is_a?(o, Integer) && o > 0
      return self ** o.to_f
    end

    redo_coerced :**, o
  end
  Primitive.always_split self, :**

  def [](index, len = undefined)
    if Primitive.is_a?(index, Range)
      Truffle::IntegerOperations.bits_reference_range(self, index)
    else
      index = Primitive.rb_to_int(index)
      if Primitive.undefined?(len)
        index < 0 ? 0 : (self >> index) & 1
      else
        (self >> index) & ((1 << len) - 1)
      end
    end
  end

  def allbits?(mask)
    mask = Primitive.rb_to_int(mask)
    (self & mask) == mask
  end

  def anybits?(mask)
    mask = Primitive.rb_to_int(mask)
    (self & mask) != 0
  end

  def ceil(precision = 0)
    return self unless precision < 0
    return 0 if self == 0

    x = 10 ** precision.abs
    if self % x == 0
      self
    else
      (self / x + 1) * x
    end
  end

  def ceildiv(other)
    -div(-other)
  end

  def coerce(other)
    if Primitive.is_a?(other, Integer)
      return [other, self]
    end

    [Float(other), Float(self)]
  end

  def divmod(b)
    divmod = Primitive.integer_divmod self, b
    unless Primitive.undefined?(divmod)
      return divmod
    end

    raise ZeroDivisionError if b == 0
    [
      (self / b).floor,
      self - b * (self / b).floor
    ]
  end

  def fdiv(n)
    if Primitive.is_a?(n, Integer)
      Primitive.integer_fdiv(self, n)
    else
      redo_coerced :fdiv, n
    end
  end

  def floor(precision = 0)
    return self unless precision < 0
    x = 10 ** precision.abs
    (self / x) * x
  end

  def nobits?(mask)
    mask = Primitive.rb_to_int(mask)
    (self & mask) == 0
  end

  def pow(exponent, modulus = undefined)
    return self ** exponent if Primitive.undefined?(modulus)

    raise TypeError, '2nd argument not allowed unless a 1st argument is integer' unless Primitive.is_a?(exponent, Integer)
    raise TypeError, '2nd argument not allowed unless all arguments are integers' unless Primitive.is_a?(modulus, Integer)
    raise RangeError, '1st argument cannot be negative when 2nd argument specified' if exponent.negative?

    # Ruby implementation is much faster than Java implementation for modulus <= 2^32,
    # and Java is much faster for modulus > 2^32.
    if Primitive.integer_fits_into_int?(modulus)
      Truffle::IntegerOperations.modular_exponentiation(self, exponent, modulus)
    else
      Primitive.mod_pow(self, exponent, modulus)
    end
  end
  Primitive.always_split self, :pow

  def times
    return to_enum(:times) { self } unless block_given?

    i = 0
    while i < self
      yield i
      i += 1
    end
    self
  end
  Primitive.always_split self, :times

  def truncate(precision = 0)
    if precision >= 0
      self
    else
      round(precision, half: :down)
    end
  end

  def chr(enc = undefined)
    if self < 0 || (self & 0xffff_ffff) != self
      subject = Primitive.integer_fits_into_int?(self) ? self : 'bignum'
      raise RangeError, "#{subject} out of char range"
    end

    if Primitive.undefined? enc
      if 0xff < self
        enc = Encoding.default_internal
        if Primitive.nil? enc
          subject = Primitive.integer_fits_into_int?(self) ? self : 'bignum'
          raise RangeError, "#{subject} out of char range"
        end
      elsif self < 0x80
        enc = Encoding::US_ASCII
      else
        enc = Encoding::BINARY
      end
    else
      enc = Truffle::Type.coerce_to_encoding enc
    end

    Primitive.string_from_codepoint self, enc
  end

  def round(ndigits = undefined, half: :up)
    return self if Primitive.undefined? ndigits

    if Primitive.is_a?(ndigits, Float) && ndigits.infinite?
      raise RangeError, "float #{ndigits} out of range of integer"
    end

    ndigits = Primitive.rb_num2int ndigits

    if ndigits >= 0
      case half
      when :up, nil, :down, :even
        self
      else
        raise ArgumentError, "invalid rounding mode: #{half}"
      end
    else
      ndigits = -ndigits

      # We want to return 0 if 10 ** ndigits / 2 > self.abs, or, taking
      # log_256 of both sides, if log_256(10 ** ndigits / 2) > self.size.
      # We have log_256(10) > 0.415241 and log_256(2) = 0.125, so:
      return 0 if 0.415241 * ndigits - 0.125 > size

      f = 10 ** ndigits

      if Primitive.is_a?(self, Integer) and Primitive.is_a?(f, Integer)
        x = self < 0 ? -self : self
        case half
        when :up, nil
          x = (x + (f / 2)) / f
        when :down
          x = x / f
        when :even
          x = (x + (f / 2)) / f
          x = (x / 2) * 2
        else
          raise ArgumentError, "invalid rounding mode: #{half}"
        end
        x = x * f
        x = -x if self < 0
        return x
      end

      return 0 if Primitive.is_a?(f, Float)

      h = f / 2
      r = self % f
      n = self - r

      unless self < 0 ? r <= h : r < h
        n += f
      end

      n
    end
  end

  def gcd(other)
    raise TypeError, "Expected Integer but got #{Primitive.class(other)}" unless Primitive.is_a?(other, Integer)
    min = self.abs
    max = other.abs
    while min > 0
      tmp = min
      min = max % min
      max = tmp
    end
    max
  end

  def rationalize(eps = nil)
    Rational(self, 1)
  end

  def numerator
    self
  end

  def denominator
    1
  end

  def to_r
    Rational(self, 1)
  end

  def lcm(other)
    raise TypeError, "Expected Integer but got #{Primitive.class(other)}" unless Primitive.is_a?(other, Integer)
    if self.zero? or other.zero?
      0
    else
      (self.div(self.gcd(other)) * other).abs
    end
  end

  def gcdlcm(other)
    gcd = self.gcd(other)
    if self.zero? or other.zero?
      [gcd, 0]
    else
      [gcd, (self.div(gcd) * other).abs]
    end
  end

  def next
    self + 1
  end
  alias_method :succ, :next

  def integer?
    true
  end

  def even?
    self & 1 == 0
  end

  def odd?
    self & 1 == 1
  end

  def ord
    self
  end

  def pred
    self - 1
  end

  def digits(base = 10)
    raise Math::DomainError, 'out of domain' if negative?
    base = Primitive.rb_to_int(base)
    raise ArgumentError, 'negative radix' if base < 0
    raise ArgumentError, "invalid radix #{base}" if base < 2
    return [0] if self == 0

    result = []
    x = self
    while x > 0
      result << x % base
      x /= base
    end
    result
  end

  def zero?
    self == 0
  end

  def self.try_convert(obj)
    Truffle::Type.try_convert(obj, Integer, :to_int)
  end

  def self.sqrt(n)
    n = Primitive.rb_to_int(n)
    raise Math::DomainError if n.negative?
    return Math.sqrt(n).floor if n < 0xfffffffffffff

    shift = n.bit_length / 4
    x = sqrt(n >> (2 * shift)) << shift
    x = (x + n / x) / 2
    xx = x * x
    while xx > n
      xx -= 2 * x - 1
      x -= 1
    end
    x
  end

  private def upto_internal(val)
    return to_enum(:upto, val) { self <= val ? val - self + 1 : 0 } unless block_given?

    i = self
    while i <= val
      yield i
      i += 1
    end
    self
  end
  Primitive.always_split self, :upto_internal

  private def downto_internal(val)
    return to_enum(:downto, val) { self >= val ? self - val + 1 : 0 } unless block_given?

    i = self
    while i >= val
      yield i
      i -= 1
    end
    self
  end
  Primitive.always_split self, :downto_internal

  class << self
    undef_method :new
  end

  def self.__allocate__
    raise TypeError, "allocator undefined for #{self}"
  end
end
