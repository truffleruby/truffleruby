# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.read" do

  describe "with an Array" do

    before :each do
      @array = [1, 2, 3]
    end

    it "reads a value of an index that exists" do
      Truffle::Interop.read_array_element(@array, 1).should == 2
    end

    it "reads a method of given name that exists" do
      Truffle::Interop.read_member(@array, "[]").should == @array.method(:[])
    end

    it "raises for an index that doesn't exist" do
      -> { Truffle::Interop.read_array_element(@array, 100) }.should raise_error(IndexError)
    end

  end

  describe "with a name that starts with @" do

    before :each do
      @object = TruffleInteropSpecs::InteropKeysClass.new
    end

    it "that exists as an instance variable reads it" do
      Truffle::Interop.read_member(@object, :@b).should == 2
    end

    it "that does not exist as an instance variable raises" do
      -> { Truffle::Interop.read_member(@object, :@foo) }.should raise_error NameError
    end

  end

  describe "with an object with a method of the same name" do

    it "produces a bound Method" do
      object = TruffleInteropSpecs::InteropKeysClass.new
      Truffle::Interop.read_member(object, :foo).call.should == 14
    end

  end

  describe "with an object with an index method" do

    it "calls the index method" do
      object = TruffleInteropSpecs::PolyglotArray.new
      value = Object.new
      Truffle::Interop.write_array_element(object, 2, value)
      Truffle::Interop.read_array_element(object, 2).should == value
      object.log.should include([:polyglot_read_array_element, 2])
    end

  end

  describe "with both an object with a method of the same name and an index method" do

    it "calls the index method" do
      object = TruffleInteropSpecs::PolyglotMember.new
      Truffle::Interop.write_member(object, :bob,  14)
      Truffle::Interop.read_member(object, :bob).should == 14
      Truffle::Interop.members(object).should include 'bob'
      object.log.should include [:polyglot_read_member, 'bob']
    end

  end

  describe "with an object without a method of the same name or an index method" do

    it "raises UnknownIdentifierException" do
      object = Object.new
      -> {
        Truffle::Interop.read_member(object, :foo)
      }.should raise_error(NameError, /Unknown identifier: foo/)
    end

  end

  describe "with a Proc" do

    it "does not call the proc" do
      proc = -> { raise 'called' }
      -> { Truffle::Interop.read_member(proc, :key) }.should raise_error NameError
      -> { Truffle::Interop.read_member(proc, :@var) }.should raise_error NameError
      Truffle::Interop.read_member(proc, 'call').should == proc.method(:call)
    end

  end

  describe "with a Hash class" do

    it "does not call the [] method" do
      -> { Truffle::Interop.read_member(Hash, :nothing) }.should raise_error NameError
    end

  end

end
