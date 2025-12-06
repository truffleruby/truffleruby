# frozen_string_literal: true

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

#
#   complex.rb -
#   	$Release Version: 0.5 $
#   	$Revision: 1.3 $
#   	$Date: 1998/07/08 10:05:28 $
#   	by Keiju ISHITSUKA(SHL Japan Inc.)
#

class Complex < Numeric

  undef_method :%, :<, :<=, :>, :>=, :between?, :clamp, # comparable
               :div, :divmod, :floor, :ceil, :modulo, :remainder,
               :round, :step, :truncate, :i, :negative?, :positive?

  def self.convert(real, imag = undefined, exception: true)
    if Primitive.check_real?(real) && Primitive.check_real?(imag)
      return new(real, imag)
    end

    Truffle::ComplexOperations.convert_not_real_arguments(real, imag, exception)
  end
  private_class_method :convert

  def Complex.generic?(other) # :nodoc:
    Primitive.is_a?(other, Integer) or
    Primitive.is_a?(other, Float) or
    (defined?(Rational) and Primitive.is_a?(other, Rational))
  end

  def Complex.rect(real, imag = 0)
    raise TypeError, 'not a real' unless Primitive.check_real?(real) && Primitive.check_real?(imag)
    new(real, imag)
  end
  class << self; alias_method :rectangular, :rect end

  def Complex.polar(r, theta = 0)
    raise TypeError, 'not a real' unless Primitive.check_real?(r) && Primitive.check_real?(theta)

    Complex(r*Math.cos(theta), r*Math.sin(theta))
  end

  attr_reader :real, :imag
  alias_method :imaginary, :imag

  def initialize(a, b = 0)
    @real = a
    @imag = b
    Primitive.freeze(self)
  end

  def -@
    Complex(-real, -imag)
  end

  def +(other)
    if Primitive.is_a?(other, Complex)
      Complex(real + other.real, imag + other.imag)
    elsif Primitive.is_a?(other, Numeric) && other.real?
      Complex(real + other, imag)
    else
      redo_coerced(:+, other)
    end
  end

  def -(other)
    if Primitive.is_a?(other, Complex)
      Complex(real - other.real, imag - other.imag)
    elsif Primitive.is_a?(other, Numeric) && other.real?
      Complex(real - other, imag)
    else
      redo_coerced(:-, other)
    end
  end

  def *(other)
    if Primitive.is_a?(other, Complex)
      Complex(real * other.real - imag * other.imag,
              real * other.imag + imag * other.real)
    elsif Primitive.is_a?(other, Numeric) && other.real?
      Complex(real * other, imag * other)
    else
      redo_coerced(:*, other)
    end
  end

  def /(other)
    if Primitive.is_a?(other, Complex)
      self * other.conjugate / other.abs2
    elsif Primitive.is_a?(other, Numeric) && other.real?
      Complex(real.quo(other), imag.quo(other))
    else
      redo_coerced(:quo, other)
    end
  end
  alias_method :quo, :/

  def ** (other)
    if !Primitive.is_a?(other, Float) && other == 0
      return Complex(1)
    end
    if Primitive.is_a?(other, Complex)
      r, theta = polar
      ore = other.real
      oim = other.imag
      nr = Math.exp(ore*Math.log(r) - oim * theta)
      ntheta = theta*ore + oim*Math.log(r)
      Complex.polar(nr, ntheta)
    elsif Primitive.is_a?(other, Integer)
      if other > 0
        x = self
        z = x
        n = other - 1
        while n != 0
          while n.even?
            x = Complex(x.real*x.real - x.imag*x.imag, 2*x.real*x.imag)
            n /= 2
          end
          z *= x
          n -= 1
        end
        z
      else
        if defined? Rational
          (Rational.__send__(:new_already_canonical, 1, 1) / self) ** -other
        else
          self ** Float(other)
        end
      end
    elsif Complex.generic?(other)
      r, theta = polar
      Complex.polar(r**other, theta*other)
    else
      x, y = other.coerce(self)
      x**y
    end
  end

  def abs
    Math.hypot(@real, @imag)
  end
  alias_method :magnitude, :abs

  def abs2
    @real*@real + @imag*@imag
  end

  def arg
    Math.atan2(@imag, @real)
  end
  alias_method :angle, :arg
  alias_method :phase, :arg

  def polar
    [abs, arg]
  end

  def conjugate
    Complex(@real, -@imag)
  end
  alias_method :conj, :conjugate

  def ==(other)
    if Primitive.is_a?(other, Complex)
      real == other.real && imag == other.imag
    elsif Primitive.is_a?(other, Numeric) && other.real?
      real == other && imag == 0
    else
      other == self
    end
  end

  def eql?(other)
    Primitive.is_a?(other, Complex) and
    Primitive.class(imag) == Primitive.class(other.imag) and
    Primitive.class(real) == Primitive.class(other.real) and
    self == other
  end

  def coerce(other)
    if Primitive.is_a?(other, Numeric) && other.real?
      [Complex.new(other, 0), self]
    elsif Primitive.is_a?(other, Complex)
      [other, self]
    else
      raise TypeError, "#{Primitive.class(other)} can't be coerced into Complex"
    end
  end

  def denominator
    @real.denominator.lcm(@imag.denominator)
  end

  def numerator
    cd = denominator
    Complex(@real.numerator*(cd/@real.denominator),
            @imag.numerator*(cd/@imag.denominator))
  end

  def real?
    false
  end

  def finite?
    @real.finite? and @imag.finite?
  end

  def infinite?
    magnitude.infinite?
  end

  def rect
    [@real, @imag]
  end
  alias_method :rectangular, :rect

  def to_c
    self
  end

  def to_f
    raise RangeError, "can't convert #{self} into Float" unless !Primitive.is_a?(imag, Float) && imag == 0
    real.to_f
  end

  def to_i
    raise RangeError, "can't convert #{self} into Integer" unless !Primitive.is_a?(imag, Float) && imag == 0
    real.to_i
  end

  def to_r
    raise RangeError, "can't convert #{self} into Rational" unless !Primitive.is_a?(imag, Float) && imag == 0
    real.to_r
  end

  def rationalize(eps = nil)
    raise RangeError, "can't' convert #{self} into Rational" unless !Primitive.is_a?(imag, Float) && imag == 0
    real.rationalize(eps)
  end

  def to_s
    result = real.to_s

    if imag < 0 || Primitive.equal?(imag, -0.0)
      result << '-'
    else
      result << '+'
    end

    imag_s = imag.abs.to_s
    result << imag_s

    unless imag_s[-1] =~ /\d/
      result << '*'
    end

    result << 'i'
    result
  end

  # Random number for hash codes. Stops hashes for similar values in
  # different classes from clashing, but defined as a constant so
  # that hashes will be deterministic.

  CLASS_SALT = 0x37f7c8ee

  private_constant :CLASS_SALT

  def hash
    val = Primitive.vm_hash_start CLASS_SALT
    val = Primitive.vm_hash_update val, @real.hash
    val = Primitive.vm_hash_update val, @imag.hash
    Primitive.vm_hash_end val
  end

  def inspect
    "(#{self})"
  end

  def fdiv(other)
    raise TypeError, "#{Primitive.class(other)} can't be coerced into Complex" unless Primitive.is_a?(other, Numeric)

    # FIXME
    self / other
  end

  private def marshal_dump
    [@real, @imag]
  end

  private def marshal_load(ary)
    @real, @imag = ary
    Primitive.freeze(self)
  end

  def <=>(other)
    if imag == 0 && Primitive.is_a?(other, Numeric)
      if Primitive.is_a?(other, Complex) && other.imag == 0
        real <=> other.real
      elsif other.real?
        real <=> other
      end
    end
  end

  I = Complex(0, 1)

end
