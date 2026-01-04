# frozen_string_literal: true

# Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Warning
  extend self

  def warn(message, category: nil)
    Truffle::Type.rb_check_type(message, String)
    unless message.encoding.ascii_compatible?
      raise Encoding::CompatibilityError, "ASCII incompatible encoding: #{message.encoding}"
    end

    unless Primitive.nil?(category)
      Truffle::Type.rb_check_type(category, Symbol)
      Truffle::WarningOperations.check_category(category)

      if category == :deprecated && !Warning[:deprecated]
        return nil
      end

      if category == :experimental && !Warning[:experimental]
        return nil
      end
    end

    $stderr.write message
    nil
  end

  def self.categories
    %i[deprecated experimental performance]
  end

  def self.[](category)
    Truffle::Type.rb_check_type(category, Symbol)

    case category
    when :deprecated
      Primitive.warning_get_category(:deprecated)
    when :experimental
      Primitive.warning_get_category(:experimental)
    when :performance
      Primitive.warning_get_category(:performance)
    else
      raise ArgumentError, "unknown category: #{category}"
    end
  end

  def self.[]=(category, value)
    Truffle::Type.rb_check_type(category, Symbol)

    case category
    when :deprecated
      Primitive.warning_set_category(:deprecated, Primitive.as_boolean(value))
    when :experimental
      Primitive.warning_set_category(:experimental, Primitive.as_boolean(value))
    when :performance
      Primitive.warning_set_category(:performance, Primitive.as_boolean(value))
    else
      raise ArgumentError, "unknown category: #{category}"
    end
  end
end
