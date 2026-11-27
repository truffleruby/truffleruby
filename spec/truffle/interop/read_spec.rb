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
      -> { Truffle::Interop.read_array_element(@array, 100) }.should.raise(IndexError)
    end

  end

  describe "with a Module" do

    before :each do
      included = Module.new
      included.const_set(:INHERITED, :inherited)

      @module = Module.new do
        include included

        def self.const_missing(name)
          raise "const_missing should not be called for #{name}"
        end

        def self.OnlyMethod
        end
      end
      @module.const_set(:CONST, 42)
      @module.const_set(:PRIVATE, :private)
      @module.private_constant :PRIVATE
    end

    it "reads a constant of the module" do
      Truffle::Interop.member_readable?(@module, "CONST").should == true
      Truffle::Interop.read_member(@module, "CONST").should == 42
    end

    it "reads an inherited constant" do
      Truffle::Interop.member_readable?(@module, "INHERITED").should == true
      Truffle::Interop.read_member(@module, "INHERITED").should == :inherited
    end

    it "does not read a top-level constant, and reads a method of that name if it exists" do
      Truffle::Interop.member_readable?(@module, "Integer").should == true
      Truffle::Interop.read_member(@module, "Integer").should == @module.method(:Integer)

      Truffle::Interop.member_readable?(@module, "Comparable").should == false
      -> { Truffle::Interop.read_member(@module, "Comparable") }.should.raise(NameError)
    end

    it "does not read a private constant" do
      Truffle::Interop.member_readable?(@module, "PRIVATE").should == false
      -> { Truffle::Interop.read_member(@module, "PRIVATE") }.should.raise(NameError)
    end

    it "does not call const_missing" do
      Truffle::Interop.member_readable?(@module, "UNDEFINED").should == false
      -> { Truffle::Interop.read_member(@module, "UNDEFINED") }.should.raise(NameError)
    end

    it "reports read side effects only for an autoload constant" do
      @module.autoload :AUTOLOAD, fixture(__FILE__, "autoload_constant_not_loaded.rb")
      Truffle::Interop.member_readable?(@module, "AUTOLOAD").should == true
      Truffle::Interop.has_member_read_side_effects?(@module, "AUTOLOAD").should == true

      Truffle::Interop.has_member_read_side_effects?(@module, "CONST").should == false
      Truffle::Interop.has_member_read_side_effects?(@module, "UNDEFINED").should == false
      Truffle::Interop.has_member_read_side_effects?(@module, "name").should == false
    end

    it "reads a method of given name that exists" do
      Truffle::Interop.read_member(@module, "name").should == @module.method(:name)
      Truffle::Interop.read_member(@module, "OnlyMethod").should == @module.method(:OnlyMethod)
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
      -> { Truffle::Interop.read_member(@object, :@foo) }.should.raise NameError
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
      object.log.should.include?([:polyglot_read_array_element, 2])
    end

  end

  describe "with both an object with a method of the same name and an index method" do

    it "calls the index method" do
      object = TruffleInteropSpecs::PolyglotMember.new
      Truffle::Interop.write_member(object, :bob,  14)
      Truffle::Interop.read_member(object, :bob).should == 14
      Truffle::Interop.members(object).should.include? 'bob'
      object.log.should.include? [:polyglot_read_member, 'bob']
    end

  end

  describe "with an object without a method of the same name or an index method" do

    it "raises UnknownIdentifierException" do
      object = Object.new
      -> {
        Truffle::Interop.read_member(object, :foo)
      }.should.raise(NameError, /Unknown identifier: foo/)
    end

  end

  describe "with a Proc" do

    it "does not call the proc" do
      proc = -> { raise 'called' }
      -> { Truffle::Interop.read_member(proc, :key) }.should.raise NameError
      -> { Truffle::Interop.read_member(proc, :@var) }.should.raise NameError
      Truffle::Interop.read_member(proc, 'call').should == proc.method(:call)
    end

  end

  describe "with a Hash class" do

    it "does not call the [] method" do
      -> { Truffle::Interop.read_member(Hash, :nothing) }.should.raise NameError
    end

  end

end
