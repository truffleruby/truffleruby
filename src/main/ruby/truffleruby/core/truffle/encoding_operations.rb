# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module EncodingOperations
    # A map with three kinds of entries:
    # * An original encoding:
    #   name.upcase.to_sym => [nil, encoding]
    # * An alias of an original encoding:
    #   alias_name.upcase.to_sym => [alias_name, original_encoding]
    # * An unset default encoding:
    #   name.upcase.to_sym => [name, nil]
    EncodingMap = {}

    def self.build_encoding_map
      Encoding.list.each do |encoding|
        key = encoding.name.upcase.to_sym
        EncodingMap[key] = [nil, encoding]
      end

      Primitive.encoding_each_alias -> alias_name, encoding do
        key = alias_name.upcase.to_sym
        EncodingMap[key] = [alias_name, encoding]
      end
    end

    def self.setup_default_encoding(name, key)
      enc = Primitive.encoding_get_default_encoding name
      EncodingMap[key] = [name, enc]
      enc
    end

    def self.change_default_encoding(name, obj)
      raise unless Primitive.is_a?(obj, Encoding) || Primitive.nil?(obj)
      key = name.upcase.to_sym
      EncodingMap[key][1] = obj
    end

    def self.dummy_encoding(name)
      new_encoding, index = Primitive.encoding_create_dummy name
      EncodingMap[name.upcase.to_sym] = [nil, new_encoding]
      [new_encoding, index]
    end

    def self.define_alias(encoding, alias_name)
      key = alias_name.upcase.to_sym
      EncodingMap[key] = [alias_name, encoding]
      Primitive.encoding_define_alias(encoding, key)
    end
  end
end
