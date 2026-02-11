# frozen_string_literal: true

# Copyright (c) 2019, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module HashOperations
    def self.hash_merge(current, other)
      new_hash = current.dup
      other.each_pair do |k, v|
        new_hash[k] = v
      end
      new_hash
    end

    # MRI: rb_hash_set_pair (rb_ary_to_h also contains similar logic for Array#to_h)
    def self.assoc_key_value_pair(hash, pair)
      ary = Truffle::Type.rb_check_convert_type pair, Array, :to_ary

      unless ary
        raise TypeError, "wrong element type #{Primitive.class(pair)} (expected array)"
      end

      if ary.size != 2
        raise ArgumentError, "element has wrong array length (expected 2, was #{ary.size})"
      end

      hash[ary[0]] = ary[1]
    end

    # MRI: extracted from rb_ary_to_h, is similar to rb_hash_set_pair
    def self.assoc_key_value_pair_with_position(hash, pair, index)
      ary = Truffle::Type.rb_check_convert_type pair, Array, :to_ary

      unless ary
        raise TypeError, "wrong element type #{Primitive.class(pair)} at #{index} (expected array)"
      end

      if ary.size != 2
        raise ArgumentError, "wrong array length at #{index} (expected 2, was #{ary.size})"
      end

      hash[ary[0]] = ary[1]
    end

    def self.lookup_raise_if_nil_default(hash, key)
      hash.fetch(key) do
        value = hash.default(key)
        if Primitive.nil?(value)
          raise KeyError.new("key{#{key}} not found", receiver: hash, key: key)
        end
        value
      end
    end
  end
end
