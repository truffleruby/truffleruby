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

module Truffle
  module EnumerableOperations
    # Packs values yielded by a source like rb_enum_values_pack() in CRuby:
    # no values -> nil, a single value -> the value, multiple values -> an Array.
    def self.pack_values(values)
      values.length >= 2 ? values : values.first
    end

    def self.cycle_size(enum_size, many)
      if many
        many = Primitive.rb_num2int many
        many = 0 if many < 0
        Primitive.nil?(enum_size) ? nil : enum_size * many
      else
        Primitive.nil?(enum_size) ? nil : Float::INFINITY
      end
    end

    class Compensation
      attr_accessor :value

      def initialize
        @value = 0.0
      end
    end

    # Kahan-Babushka-Neumaier balancing compensated summation algorithm, like in CRuby
    # See https://en.wikipedia.org/wiki/Kahan_summation_algorithm#Further_enhancements
    def self.sum_add(a, b, compensation)
      floats = false
      lhs = 0.0
      rhs = 0.0
      if Primitive.is_a?(a, Float)
        if Primitive.is_a?(b, Float)
          lhs = a
          rhs = b
          floats = true
        elsif Primitive.is_a?(b, Integer) || Primitive.is_a?(b, Rational)
          lhs = a
          rhs = b.to_f
          floats = true
        end
      elsif Primitive.is_a?(b, Float)
        if Primitive.is_a?(a, Integer) || Primitive.is_a?(a, Rational)
          lhs = a.to_f
          rhs = b
          floats = true
        end
      end

      return a + b unless floats

      t = lhs + rhs
      if t.finite?
        compensation.value += lhs.abs >= rhs.abs ? ((lhs - t) + rhs) : ((rhs - t) + lhs)
      end
      t
    end
  end
end
