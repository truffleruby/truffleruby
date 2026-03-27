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
  describe "java_class.new" do

    it "creates basic objects" do
      big_integer = Truffle::Interop.java_type("java.math.BigInteger").new("14")
      big_integer.getClass.getName.should == "java.math.BigInteger"
      big_integer.intValue.should == 14
    end

    it "calls appropriate overloads" do
      big_integer = Truffle::Interop.java_type("java.math.BigInteger").new("14", 8)
      big_integer.getClass.getName.should == "java.math.BigInteger"
      big_integer.intValue.should == 12
    end

    it "creates arrays of objects" do
      array = Truffle::Interop.java_type("java.lang.Integer[]").new(3)
      array[1] = 14
      array[0].nil?.should be_true
      array[1].should == 14
      array[2].nil?.should be_true
      Truffle::Interop.array_size(array).should == 3
    end

    it "creates arrays of arrays of objects" do
      array = Truffle::Interop.java_type("java.lang.Integer[][]").new(3)
      array[1] = Truffle::Interop.java_type("java.lang.Integer[]").new(1)
      array[1][0] = 14
      array[0].nil?.should be_true
      Truffle::Interop.array_size(array[1]).should == 1
      array[1][0].should == 14
      array[2].nil?.should be_true
      Truffle::Interop.array_size(array).should == 3
    end

    it "creates arrays of primitives" do
      array = Truffle::Interop.java_type("int[]").new(3)
      array[1] = 14
      array[0].should == 0
      array[1].should == 14
      array[2].should == 0
      Truffle::Interop.array_size(array).should == 3
    end

  end
end
