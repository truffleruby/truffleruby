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
  describe "Truffle::Interop.from_java_array" do

    it "converts a byte[]" do
      array = Truffle::Interop.java_type("byte[]").new(3)
      array[0] = 1
      array[1] = 2
      array[2] = 3
      array = Truffle::Interop.from_java_array(array)
      array.should == [1, 2, 3]
    end

    it "converts a short[]" do
      array = Truffle::Interop.java_type("short[]").new(3)
      array[0] = 1
      array[1] = 2
      array[2] = 3
      array = Truffle::Interop.from_java_array(array)
      array.should == [1, 2, 3]
    end

    it "converts a int[]" do
      array = Truffle::Interop.java_type("int[]").new(3)
      array[0] = 1
      array[1] = 2
      array[2] = 3
      array = Truffle::Interop.from_java_array(array)
      array.should == [1, 2, 3]
    end

    it "converts a long[]" do
      array = Truffle::Interop.java_type("long[]").new(3)
      array[0] = 1
      array[1] = 2
      array[2] = 3
      array = Truffle::Interop.from_java_array(array)
      array.should == [1, 2, 3]
    end

    it "converts a float[]" do
      array = Truffle::Interop.java_type("float[]").new(3)
      array[0] = 1.0
      array[1] = 2.0
      array[2] = 3.0
      array = Truffle::Interop.from_java_array(array)
      array.should == [1.0, 2.0, 3.0]
    end

    it "converts a double[]" do
      array = Truffle::Interop.java_type("double[]").new(3)
      array[0] = 1.0
      array[1] = 2.0
      array[2] = 3.0
      array = Truffle::Interop.from_java_array(array)
      array.should == [1.0, 2.0, 3.0]
    end

    it "converts an array of objects" do
      big_integer = Truffle::Interop.java_type("java.math.BigInteger")
      java_array = Truffle::Interop.java_type("java.math.BigInteger[]").new(3)
      java_array[0] = big_integer[:ZERO]
      java_array[1] = big_integer[:ONE]
      java_array[2] = big_integer[:TEN]

      ruby_array = Truffle::Interop.from_java_array(java_array)
      ruby_array[0].equal?(big_integer[:ZERO]).should be_true
      ruby_array[1].equal?(big_integer[:ONE]).should be_true
      ruby_array[2].equal?(big_integer[:TEN]).should be_true
      ruby_array.size.should == 3
    end

    it "converts an array of boxed primitives" do
      array = Truffle::Interop.java_type("java.lang.Integer[]").new(3)
      array[0] = 1
      array[1] = 2
      array[2] = 3
      array = Truffle::Interop.from_java_array(array)
      array.should == [1, 2, 3]
    end

  end
end
