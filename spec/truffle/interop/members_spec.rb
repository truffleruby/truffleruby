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

describe "Truffle::Interop.members" do

  it "returns an array" do
    keys = Truffle::Interop.members({'a' => 1, 'b' => 2, 'c' => 3})
    keys.should.instance_of?(Array)
  end

  it "returns an array of interop strings" do
    keys = Truffle::Interop.members(Object.new)
    keys.each do |key|
      Truffle::Interop.should.string? key
      String.should === key # actually Ruby Strings
    end

    members = Truffle::Interop.members(Truffle::Debug.foreign_object_with_members)
    members.should == ["a", "b", "c", "method1", "method2"]
    members.each do |key|
      Truffle::Interop.should.string? key
      Truffle::Interop.should.java_string? key # actually Java Strings
    end
  end

  it "returns an array of public methods for an array" do
    Truffle::Interop.members([1, 2, 3]).to_set.should >= [].public_methods.map(&:to_s).to_set
  end

  it "returns an empty array for a big integer" do
    Truffle::Interop.members(bignum_value).to_set.should >= bignum_value.public_methods.map!(&:to_s).to_set
  end

  it "returns an empty array for a proc" do
    proc = proc { }
    Truffle::Interop.members(proc).to_set.should >= proc.public_methods.map!(&:to_s).to_set
  end

  it "returns an empty array for a lambda" do
    lambda = -> { }
    Truffle::Interop.members(lambda).to_set.should >= lambda.public_methods.map!(&:to_s).to_set
  end

  it "returns methods of a method" do
    method = Object.new.method :to_s
    Truffle::Interop.members(method).to_set.should >= method.public_methods.map!(&:to_s).to_set
  end

  it "returns an empty array for an object with a custom #[] method" do
    object = TruffleInteropSpecs::PolyglotMember.new
    Truffle::Interop.members(object).should == []
  end

  it "does not return the keys of a hash" do
    members = Truffle::Interop.members({'a' => 1, 'b' => 2, 'c' => 3})
    members.should_not.include?('a')
    members.should_not.include?('b')
    members.should_not.include?('c')
  end

  it "returns the methods of an object" do
    object = Object.new
    Truffle::Interop.members(object).to_set.should >= object.public_methods.map(&:to_s).to_set
  end

  it "returns the methods of a user-defined method" do
    object = TruffleInteropSpecs::InteropKeysClass.new
    Truffle::Interop.members(object).to_set.should >= object.public_methods.map(&:to_s).to_set
  end

  it "returns a user-defined method" do
    object = TruffleInteropSpecs::InteropKeysClass.new
    Truffle::Interop.members(object).should.include?('foo')
  end

  describe "with a Module" do

    before :each do
      included = Module.new
      included.const_set(:INHERITED, :inherited)

      @module = Module.new do
        include included

        def self.CONST
        end
      end
      @module.const_set(:CONST, 42)
      @module.const_set(:PRIVATE, :private)
      @module.private_constant :PRIVATE
    end

    it "returns the constants which can be read, including inherited ones" do
      members = Truffle::Interop.members(@module)
      members.should.include?('CONST')
      members.should.include?('INHERITED')
      members.each do |member|
        Truffle::Interop.member_readable?(@module, member).should == true
      end
    end

    it "does not return private constants and top-level constants" do
      members = Truffle::Interop.members(@module)
      members.should_not.include?('PRIVATE')
      members.should_not.include?('Comparable')
    end

    it "returns the methods of the module" do
      Truffle::Interop.members(@module).to_set.should >= @module.public_methods.map(&:to_s).to_set
    end

    it "returns a name only once if it is both a constant and a method" do
      Truffle::Interop.members(@module).count('CONST').should == 1
    end

    it "returns the constants of a class but not the ones of Object" do
      klass = Class.new(Struct.new(:a))
      klass.const_set(:CONST, 42)
      members = Truffle::Interop.members(klass)
      members.should.include?('CONST')
      members.should_not.include?('Comparable')
    end

    it "returns the constants of Module" do
      Module.const_set(:INTEROP_MEMBERS_SPEC, 1)
      begin
        members = Truffle::Interop.members(Module)
        members.should.include?('INTEROP_MEMBERS_SPEC')
        members.should_not.include?('Comparable')
      ensure
        Module.send(:remove_const, :INTEROP_MEMBERS_SPEC)
      end
    end

  end

  describe "without internal set" do

    it "does return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.members(hash, true).should.include?('@foo')
    end

    it "does not return instance variables of an object" do
      members = Truffle::Interop.members(TruffleInteropSpecs::InteropKeysClass.new)
      members.should_not.include?('@a')
      members.should_not.include?('@b')
      members.should_not.include?('@c')
    end

  end

  describe "with internal set" do

    it "does return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.members(hash, true).should.include?('@foo')
    end

    it "returns instance variables of an object" do
      Truffle::Interop.members(TruffleInteropSpecs::InteropKeysClass.new, true).to_set.should >= Set['@a', '@b', '@c']
    end

  end

end
