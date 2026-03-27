# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
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
  module NumericOperations
    def self.step_no_block(from, orig_limit, orig_step, by, to, limit, step)
      step = 1 if Primitive.nil?(step)

      if (Primitive.undefined?(to) || Primitive.nil?(to) || Primitive.is_a?(to, Numeric)) && Primitive.is_a?(step, Numeric)
        return Enumerator::ArithmeticSequence.new(from, :step, from, limit, step, false)
      end

      kwargs = {}
      kwargs[:by] = by unless Primitive.undefined?(by)
      kwargs[:to] = to unless Primitive.undefined?(to)
      from.to_enum(:step, orig_limit, orig_step, kwargs) do
        Truffle::NumericOperations.step_size(from, limit, step, false)
      end
    end

    def self.step_non_float(value, limit, step, desc)
      if desc
        until value < limit
          yield value
          value += step
        end
      else
        until value > limit
          yield value
          value += step
        end
      end
    end
    Primitive.always_split singleton_class, :step_non_float

    def self.step_non_float_exclude_end(value, limit, step, desc)
      if desc
        until value <= limit
          yield value
          value += step
        end
      else
        until value >= limit
          yield value
          value += step
        end
      end
    end
    Primitive.always_split singleton_class, :step_non_float_exclude_end

    def self.step_float(value, limit, step, desc, exclude_end)
      n = float_step_size(value, limit, step, exclude_end)

      if n > 0
        if step.infinite?
          yield value
        else
          i = 0
          if desc
            while i < n
              d = i * step + value
              d = limit if limit > d
              yield d
              i += 1
            end
          else
            while i < n
              d = i * step + value
              d = limit if limit < d
              yield d
              i += 1
            end
          end
        end
      end
    end
    Primitive.always_split singleton_class, :step_float

    def self.float_step_size(value, limit, step, exclude_end)
      if step.infinite?
        return step > 0 ? (value <= limit ? 1 : 0) : (value >= limit ? 1 : 0)
      end

      n = (limit - value) / step
      return 0 if n < 0

      err = (value.abs + limit.abs + (limit - value).abs) / step.abs * Float::EPSILON
      err = 0.5 if err > 0.5

      if exclude_end
        return 0 if n <= 0

        if n < 1
          n = 0
        else
          n = n.finite? && err.finite? ? (n - err).floor : n
        end
      else
        return 0 if n < 0

        n = n.finite? && err.finite? ? (n + err).floor : n
      end

      n + 1
    end

    def self.step_size(value, limit, step, exclude_end)
      value, limit, step, desc, is_float =
        step_fetch_args(value, limit, step)

      if stepping_forever?(limit, step, desc)
        Float::INFINITY
      elsif is_float
        float_step_size(value, limit, step, exclude_end)
      else
        if (desc && value < limit) || (!desc && value > limit)
          0
        else
          len = ((value - limit).abs + 1).fdiv(step.abs).ceil
          if exclude_end
            last = value + (step * (len - 1))
            last == limit ? len - 1 : len
          else
            len
          end
        end
      end
    end

    def self.stepping_forever?(limit, step, desc)
      return true if Primitive.nil?(limit) || step.zero?
      if desc
        limit == -Float::INFINITY && step != -Float::INFINITY
      else
        limit == Float::INFINITY  && step != Float::INFINITY
      end
    end

    def self.step_fetch_args(value, limit, step)
      unless Primitive.is_a?(step, Numeric)
        coerced = Truffle::Type.check_funcall(step, :>, [0])
        raise TypeError, "0 can't be coerced into #{Primitive.class(step)}" if Primitive.undefined?(coerced)
        step = coerced
      end

      desc = step < 0
      default_limit = desc ? -Float::INFINITY : Float::INFINITY

      if Primitive.is_a?(value, Float) or
          Primitive.is_a?(limit, Float) or
          Primitive.is_a?(step, Float)
        [Truffle::Type.rb_num2dbl(value), Truffle::Type.rb_num2dbl(limit || default_limit),
         Truffle::Type.rb_num2dbl(step), desc, true]
      else
        [value, limit || default_limit, step, desc, false]
      end
    end
  end
end
