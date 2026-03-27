# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Interop coercion" do

  describe "in a numerical operation" do

    it "does't interfere with normal coercion" do
      -> { 14 + true }.should raise_error(TypeError, /can't be coerced into Integer/)
      -> { 14 * true }.should raise_error(TypeError, /can't be coerced into Integer/)
      -> { 14.2 + true }.should raise_error(TypeError, /can't be coerced into Float/)
      -> { 14.2 * true }.should raise_error(TypeError, /can't be coerced into Float/)
      (14 + 2.0).should == 16.0
      (14.0 + 2.0).should == 16.0
    end

    it "unboxes a LHS operand" do
      (Truffle::Debug.foreign_boxed_value(2) + 14).should == 16
    end

    it "unboxes a RHS operand" do
      (2 + Truffle::Debug.foreign_boxed_value(14)).should == 16
    end

  end

  describe "in condition" do
    it "coerce foreign boolean" do
      (Truffle::Debug.foreign_boxed_value(true) ? 1 : 2).should == 1
      (Truffle::Debug.foreign_boxed_value(false) ? 1 : 2).should == 2
      (Truffle::Debug.foreign_boxed_value(:other) ? 1 : 2).should == 1
    end

    it "coerce null as falsy value" do
      foreign_null = Truffle::Debug.foreign_null ? true : false
      foreign_null.should be_false

      [Object.new, foreign_null].map { |x| x ? true : false }.should == [true, false]
    end
  end

end
