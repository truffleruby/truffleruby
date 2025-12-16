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

class Numeric
  include Comparable

  def clone(freeze: nil)
    if Primitive.false?(freeze)
      raise ArgumentError, "can't unfreeze #{Primitive.class(self).name}"
    end
    self
  end

  def +@
    self
  end

  def -@
    0 - self
  end

  def divmod(other)
    [div(other), self % other]
  end

  def eql?(other)
    return false unless Primitive.class(other) == Primitive.class(self)
    self == other
  end

  def <=>(other)
    # It's important this method NOT contain the coercion protocols!
    # MRI doesn't and doing so breaks stuff!

    return 0 if Primitive.equal?(self, other)
    nil
  end

  def step(orig_limit = undefined, orig_step = undefined, by: undefined, to: undefined, &block)
    uses_kwargs = false
    limit = if Primitive.undefined?(to)
              Primitive.undefined?(orig_limit) ? nil : orig_limit
            else
              raise ArgumentError, 'to is given twice' unless Primitive.undefined?(orig_limit)
              uses_kwargs = true
              to
            end
    step = if Primitive.undefined?(by)
             Primitive.undefined?(orig_step) ? 1 : orig_step
           else
             raise ArgumentError, 'step is given twice' unless Primitive.undefined?(orig_step)
             uses_kwargs = true
             by
           end

    unless block_given?
      return Truffle::NumericOperations.step_no_block(self, orig_limit, orig_step, by, to, limit, step, uses_kwargs)
    end

    value, limit, step, desc, is_float =
      Truffle::NumericOperations.step_fetch_args(self, limit, step, uses_kwargs)

    if is_float
      Truffle::NumericOperations.step_float(value, limit, step, desc, &block)
    else
      Truffle::NumericOperations.step_non_float(value, limit, step, desc, &block)
    end

    self
  end
  Primitive.always_split self, :step

  def truncate
    Float(self).truncate
  end

  # Delegate #to_int to #to_i in subclasses
  def to_int
    to_i
  end

  def integer?
    false
  end

  def zero?
    self == 0
  end

  def nonzero?
    zero? ? nil : self
  end

  def positive?
    self > 0
  end

  def negative?
    self < 0
  end

  def finite?
    true
  end

  def infinite?
    nil
  end

  def round
    to_f.round
  end

  def abs
    self < 0 ? -self : self
  end

  def floor
    Truffle::Type.rb_num2dbl(self).floor
  end

  def ceil
    Truffle::Type.rb_num2dbl(self).ceil
  end

  def remainder(other)
    mod = self % other

    if mod != 0 and ((self < 0 and other > 0) or (self > 0 and other < 0))
      mod - other
    else
      mod
    end
  end

  def coerce(other)
    other = Truffle::Interop.unbox_if_needed(other)

    if other.instance_of? Primitive.class(self)
      return [other, self]
    end

    [Float(other), Float(self)]
  end

  ##
  # This method mimics the semantics of MRI's do_coerce function
  # in numeric.c. Note these differences between it and #coerce:
  #
  #   1.2.coerce("2") => [2.0, 1.2]
  #   1.2 + "2" => TypeError: String can't be coerced into Float
  #
  # See also Integer#coerce

  def math_coerce(other, error = :coerce_error)
    other = Truffle::Interop.unbox_if_needed(other)
    return math_coerce_error(other, error) unless other.respond_to?(:coerce, true)

    values = other.__send__(:coerce, self)

    unless Primitive.is_a?(values, Array) && values.length == 2
      if error == :no_error
        return nil
      else
        raise TypeError, 'coerce must return [x, y]'
      end
    end

    [values[1], values[0]]
  end
  private :math_coerce

  def math_coerce_error(other, error)
    if error == :coerce_error
      raise TypeError, "#{Primitive.class(other)} can't be coerced into #{Primitive.class(self)}"
    elsif error == :compare_error
      raise ArgumentError, "comparison of #{Primitive.class(self)} with #{Primitive.class(other)} failed"
    elsif error == :bad_coerce_return_error
      nil
    elsif error == :no_error
      nil
    end
  end
  private :math_coerce_error

  def bit_coerce(other)
    values = math_coerce(other)
    unless Primitive.is_a?(values[0], Integer) && Primitive.is_a?(values[1], Integer)
      raise TypeError, "#{Primitive.class(values[1])} can't be coerced into #{Primitive.class(self)}"
    end
    values
  end
  private :bit_coerce

  def redo_coerced(meth, right, *extra)
    b, a = math_coerce(right)
    a.__send__ meth, b, *extra
  end
  private :redo_coerced

  def redo_compare(meth, right)
    b, a = math_coerce(right, :compare_error)
    return nil unless b
    a.__send__ meth, b
  end
  private :redo_compare

  private def redo_compare_no_error(right)
    b, a = math_coerce(right, :no_error)
    return nil unless b
    a <=> b
  end

  # only used by Float#<=>
  private def redo_compare_bad_coerce_return_error(right)
    if self.infinite? and !Primitive.undefined?(val = Truffle::Type.check_funcall(right, :infinite?))
      if val
        cmp = val <=> 0
        return self > 0.0 ? (cmp > 0 ? 0 : 1) : (cmp < 0 ? 0 : -1)
      else
        # right.infinite? returned false or nil, which means right is finite
        return self > 0.0 ? 1 : -1
      end
    end
    b, a = math_coerce(right, :bad_coerce_return_error)
    return nil unless b
    a <=> b
  end

  def redo_bit_coerced(meth, right)
    b, a = bit_coerce(right)
    a.__send__ meth, b
  end
  private :redo_bit_coerced

  def div(other)
    raise ZeroDivisionError, 'divided by 0' if other == 0
    (self / other).floor
  end

  def fdiv(other)
    self.to_f / other
  end

  def quo(other)
    Rational.__send__(:convert, self, 1, true) / other
  end

  def modulo(other)
    self - other * self.div(other)
  end
  alias_method :%, :modulo

  def i
    Complex(0, self)
  end

  def to_c
    Complex(self, 0)
  end

  def real
    self
  end

  def imag
    0
  end
  alias_method :imaginary, :imag

  def arg
    if self < 0
      Math::PI
    else
      0
    end
  end
  alias_method :angle, :arg
  alias_method :phase, :arg

  def polar
    [abs, arg]
  end

  def conjugate
    self
  end
  alias_method :conj, :conjugate

  def rect
    [self, 0]
  end
  alias_method :rectangular, :rect

  def abs2
    self * self
  end

  alias_method :magnitude, :abs

  def numerator
    to_r.numerator
  end

  def denominator
    to_r.denominator
  end

  def real?
    true
  end

  def singleton_method_added(name)
    self.singleton_class.send(:remove_method, name)
    raise TypeError, "can't define singleton method #{name} for #{Primitive.class(self)}"
  end
end
