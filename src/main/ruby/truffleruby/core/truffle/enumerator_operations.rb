# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module EnumeratorOperations
    LAZY_OVERRIDE_METHODS = %i[map collect flat_map collect_concat select find_all filter filter_map reject grep grep_v
                               zip take take_while drop drop_while uniq with_index]

    def self.lazy_method(meth)
      if LAZY_OVERRIDE_METHODS.include?(meth)
        :"_enumerable_#{meth}"
      else
        meth
      end
    end

    def self.product_iterator(current, rest_enums, &block)
      return block.call(current) if rest_enums.empty?

      rest_enums_tail = rest_enums[1..]
      rest_enums.first.each_entry do |next_e|
        product_iterator(current + [next_e], rest_enums_tail, &block)
      end
    end
  end
end
