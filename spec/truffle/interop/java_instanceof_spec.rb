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
  describe "Truffle::Interop.java_instanceof?" do

    it "returns true for a directly matching Java object and class" do
      big_integer_class = Truffle::Interop.java_type("java.math.BigInteger")
      big_integer = big_integer_class.new("14")
      Truffle::Interop.java_instanceof?(big_integer, big_integer_class).should be_true
    end

    it "returns true for a matching Java object and superclass" do
      big_integer_class = Truffle::Interop.java_type("java.math.BigInteger")
      big_integer = big_integer_class.new("14")
      number_class = Truffle::Interop.java_type("java.lang.Number")
      Truffle::Interop.java_instanceof?(big_integer, number_class).should be_true
    end

    it "returns true for a matching Java object and interface" do
      big_integer_class = Truffle::Interop.java_type("java.math.BigInteger")
      big_integer = big_integer_class.new("14")
      serializable_interface = Truffle::Interop.java_type("java.io.Serializable")
      Truffle::Interop.java_instanceof?(big_integer, serializable_interface).should be_true
    end

    it "returns false for an unrelated Java object and Java class" do
      big_integer_class = Truffle::Interop.java_type("java.math.BigInteger")
      big_integer = big_integer_class.new("14")
      big_decimal_class = Truffle::Interop.java_type("java.math.BigDecimal")
      Truffle::Interop.java_instanceof?(big_integer, big_decimal_class).should be_false
    end

    it "returns false for an unrelated Ruby object and Java class" do
      big_integer_class = Truffle::Interop.java_type("java.math.BigInteger")
      Truffle::Interop.java_instanceof?(Object.new, big_integer_class).should be_false
    end

    guard -> { !Truffle::Boot.get_option('chaos-data') } do
      it "handles boxing of primitives like Java does" do
        integer_class = Truffle::Interop.java_type("java.lang.Integer")
        Truffle::Interop.java_instanceof?(14, integer_class).should be_true
        double_class = Truffle::Interop.java_type("java.lang.Double")
        Truffle::Interop.java_instanceof?(14.2, double_class).should be_true
      end
    end

    it "raises a type error if passed something that is not a Java class" do
      -> { Truffle::Interop.java_instanceof?(14, nil) }.should raise_error(TypeError)
      -> { Truffle::Interop.java_instanceof?(14, String) }.should raise_error(TypeError)
      -> { Truffle::Interop.java_instanceof?(14, Truffle::Debug.java_object) }.should raise_error(TypeError)
    end

  end
end
