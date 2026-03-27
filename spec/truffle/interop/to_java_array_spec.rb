# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.to_java_array" do

  it "creates a int[] from Fixnum values" do
    Truffle::Interop.to_java_array([1, 2, 3]).getClass.name.should == "int[]"
  end

  it "copies the correct values" do
    a = Truffle::Interop.to_java_array([1, 2, 3])
    a[0].should == 1
    a[1].should == 2
    a[2].should == 3
  end

  it "picks a sensible Java array type" do
    Truffle::Interop.to_java_array([1, 2, 3]).getClass.name.should == "int[]"
    Truffle::Interop.to_java_array([1.1, 2.2, 3.3]).getClass.name.should == "double[]"
    Truffle::Interop.to_java_array([:a, :b, :c]).getClass.name.should == "java.lang.Object[]"
  end

  it "calls #to_a" do
    mock_array = Object.new

    def mock_array.to_a
      [1, 2, 3]
    end

    Truffle::Interop.to_java_array(mock_array).getClass.name.should == "int[]"
  end

  it "creates a copy" do
    a1 = [1, 2, 3]
    a2 = Truffle::Interop.to_java_array(a1)
    a2[0].should == 1
    a1[0] = 10
    a2[0].should == 1
  end

end
