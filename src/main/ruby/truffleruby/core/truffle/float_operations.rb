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
  module FloatOperations
    class << self
      def round_overflow?(ndigits, exponent)
        ndigits >= Float::DIG + 2 - (exponent > 0 ? exponent / 4 : exponent / 3 - 1)
      end

      def round_underflow?(ndigits, exponent)
        exponent >= 0 && ndigits >= (Float::DIG + 2) - exponent / 4
      end

      def round_to_n_place(f, ndigits, half)
        case half
        when :up
          round_half_up(f, ndigits)
        when :even
          round_half_even(f, ndigits)
        when :down
          round_half_down(f, ndigits)
        end
      end

      private

      def round(d)
        if d > 0
          f = d.floor
          f + (d - f >= 0.5 ? 1 : 0)
        elsif d < 0
          f = d.ceil
          f - (f - d >= 0.5 ? 1 : 0)
        end
      end

      # MRI: round_half_up
      def round_half_up(x, ndigits)
        return round(x) if ndigits == 0

        s = 10.pow(ndigits)
        xs = x * s
        f = round(xs)

        # apply the :up strategy
        if x > 0
          if ((f + 0.5) / s) <= x
            f += 1
          end
        else
          if ((f - 0.5) / s) >= x
            f -= 1
          end
        end

        f.to_f / s
      end

      # MRI: round_half_down
      def round_half_down(x, ndigits)
        s = 10.pow(ndigits)
        xs = x * s
        f = round(xs)

        # apply the :down strategy
        if x > 0
          if ((f - 0.5) / s) >= x
            f -= 1
          end
        else
          if ((f + 0.5) / s) <= x
            f += 1
          end
        end

        ndigits == 0 ? f : f.to_f / s
      end

      # MRI: round_half_even
      def round_half_even(x, ndigits)
        s = 10.pow(ndigits)
        u = x.to_i
        v = x - u
        us = u * s;
        vs = v * s;

        if x > 0.0
          f = vs.to_i
          uf = us + f
          d = vs - f

          if d > 0.5
            d = 1
          elsif (d == 0.5 || (((uf + 0.5) / s) <= x))
            d = uf % 2
          else
            d = 0
          end

          x = f + d
        elsif x < 0.0
          f = vs.ceil
          uf = us + f
          d = f - vs

          if (d > 0.5)
            d = 1
          elsif d == 0.5 || ((uf - 0.5) / s) >= x
            d = -uf % 2
          else
            d = 0
          end

          x = f - d
        end

        if ndigits == 0
          us + x
        else
          (us + x).to_f / s
        end
      end
    end
  end
end
