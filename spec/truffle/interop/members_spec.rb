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
    keys.should be_an_instance_of(Array)
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
    Truffle::Interop.members([1, 2, 3]).should include(*([].public_methods.map(&:to_s)))
  end

  it "returns an empty array for a big integer" do
    Truffle::Interop.members(bignum_value).should include(*bignum_value.public_methods.map!(&:to_s))
  end

  it "returns an empty array for a proc" do
    proc = proc { }
    Truffle::Interop.members(proc).should include(*proc.public_methods.map!(&:to_s))
  end

  it "returns an empty array for a lambda" do
    lambda = -> { }
    Truffle::Interop.members(lambda).should include(*lambda.public_methods.map!(&:to_s))
  end

  it "returns methods of a method" do
    method = Object.new.method :to_s
    Truffle::Interop.members(method).should include(*method.public_methods.map!(&:to_s))
  end

  it "returns an empty array for an object with a custom #[] method" do
    object = TruffleInteropSpecs::PolyglotMember.new
    Truffle::Interop.members(object).should == []
  end

  it "does not return the keys of a hash" do
    Truffle::Interop.members({'a' => 1, 'b' => 2, 'c' => 3}).should_not include('a', 'b', 'c')
  end

  it "returns the methods of an object" do
    object = Object.new
    Truffle::Interop.members(object).should include(*object.public_methods.map(&:to_s))
  end

  it "returns the methods of a user-defined method" do
    object = TruffleInteropSpecs::InteropKeysClass.new
    Truffle::Interop.members(object).should include(*object.public_methods.map(&:to_s))
  end

  it "returns a user-defined method" do
    object = TruffleInteropSpecs::InteropKeysClass.new
    Truffle::Interop.members(object).should include('foo')
  end

  describe "without internal set" do

    it "does return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.members(hash, true).should include('@foo')
    end

    it "does not return instance variables of an object" do
      Truffle::Interop.members(TruffleInteropSpecs::InteropKeysClass.new).should_not include('@a', '@b', '@c')
    end

  end

  describe "with internal set" do

    it "does return the instance variables of a hash" do
      hash = {a: 1, b: 2, c: 3}
      hash.instance_variable_set(:@foo, 14)
      Truffle::Interop.members(hash, true).should include('@foo')
    end

    it "returns instance variables of an object" do
      Truffle::Interop.members(TruffleInteropSpecs::InteropKeysClass.new, true).should include('@a', '@b', '@c')
    end

  end

end
