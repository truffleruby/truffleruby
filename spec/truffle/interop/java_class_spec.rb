# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

guard -> { !TruffleRuby.native? } do
  describe "java_object.getClass" do

    it "produces the same class as java_type for objects" do
      big_integer_class = Truffle::Interop.java_type("java.math.BigInteger")
      big_integer = big_integer_class.new("14")
      big_integer.getClass.equal?(big_integer_class[:class]).should be_true
    end

    it "produces the same class as java_type for arrays of objects" do
      big_integer_array_class = Truffle::Interop.java_type("java.math.BigInteger[]")
      big_integer_array = big_integer_array_class.new(3)
      big_integer_array.getClass.equal?(big_integer_array_class[:class]).should be_true
    end

    it "produces the same class as java_type for arrays of primitives" do
      int_array_class = Truffle::Interop.java_type("int[]")
      int_array = int_array_class.new(3)
      int_array.getClass.equal?(int_array_class[:class]).should be_true
    end

  end
end
