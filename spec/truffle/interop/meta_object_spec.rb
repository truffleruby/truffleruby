# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.meta_object" do
  it "returns Integer class for an integer" do
    Truffle::Interop.meta_object(14).should == Integer
  end

  it "returns TrueClass class for a true boolean" do
    Truffle::Interop.meta_object(true).should == TrueClass
  end

  it "returns Object class for an object" do
    Truffle::Interop.meta_object(Object.new).should == Object
  end

  it "returns Hash class for a hash" do
    Truffle::Interop.meta_object({}).should == Hash
  end

  it "returns Array class for an array" do
    Truffle::Interop.meta_object([1, 2, 3]).should == Array
  end

  it "returns Range class for a range" do
    Truffle::Interop.should.has_meta_object?(1..)
    Truffle::Interop.should.has_meta_object?(1..2)
    Truffle::Interop.should.has_meta_object?((1<<60)..(1<<61))

    Truffle::Interop.meta_object(1..).should == Range
    Truffle::Interop.meta_object(1..2).should == Range
    Truffle::Interop.meta_object((1<<60)..(1<<61)).should == Range
  end

  it "returns a Ruby class implementing all meta objects methods" do
    meta = Truffle::Interop.meta_object("string")
    Truffle::Interop.meta_simple_name(meta).should == 'String'
    Truffle::Interop.meta_qualified_name(meta).should == 'String'
    Truffle::Interop.meta_parents(meta).to_a.should == [Object]

    Truffle::Interop.meta_simple_name(Enumerator::Lazy).should == 'Lazy'
    Truffle::Interop.meta_qualified_name(Enumerator::Lazy).should == 'Enumerator::Lazy'

    Truffle::Interop.should_not.has_meta_parents?(Enumerable)
  end
end

describe "Truffle::Interop.meta_object?" do
  it "returns true for a Class" do
    Truffle::Interop.should.meta_object?(String)
    Truffle::Interop.should.meta_object?(Class.new)
  end

  it "returns true for a Module" do
    Truffle::Interop.should.meta_object?(Kernel)
    Truffle::Interop.should.meta_object?(Module.new)
  end

  it "returns false for objects" do
    Truffle::Interop.should_not.meta_object?(Object.new)
  end
end
