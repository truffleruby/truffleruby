# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.remove" do

  describe "with Array" do
    before :each do
      @array = [:a, :b, :c, :d]
    end

    it "removes the element at the specified index" do
      Truffle::Interop.remove_array_element(@array, 2).should == nil
      @array.should == [:a, :b, :d]
    end

    it "raises when the index is out of bounds" do
      -> { Truffle::Interop.remove_array_element(@array, 10) }.should raise_error IndexError
      @array.should == [:a, :b, :c, :d]
    end

    it "raises a IndexError when the index is not valid" do
      -> {
        Truffle::Interop.remove_array_element(@array, -1)
      }.should raise_error(IndexError)
    end
  end

  describe "with any other type" do
    describe "with a name that starts with @" do
      before :each do
        @object = TruffleInteropSpecs::InteropKeysClass.new
      end

      it "removes an instance variable that exists" do
        Truffle::Interop.remove_member(@object, :@a).should == nil
        @object.instance_variable_defined?(:@a).should be_false
      end

      it "raises an error when the instance variable doesn't exist" do
        -> {
          Truffle::Interop.remove_member(@object, :@foo)
        }.should raise_error(NameError)
      end
    end

    describe "with a name that doesn't start with @" do
      it "raises an unsupported message error" do
        -> { Truffle::Interop.remove_array_element("abc", 1) }.
            should raise_error(Polyglot::UnsupportedMessageError)
      end
    end
  end

end
