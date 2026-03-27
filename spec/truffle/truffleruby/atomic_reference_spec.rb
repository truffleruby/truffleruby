# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require 'bigdecimal'

describe "TruffleRuby::AtomicReference" do

  it ".new creates new instance with a value and get reads it" do
    r = TruffleRuby::AtomicReference.new(:value)
    r.get.should equal :value
  end

  it ".new creates new instance with a nil value by default" do
    r = TruffleRuby::AtomicReference.new
    r.get.should equal nil
  end

  it "#set changes the value" do
    r = TruffleRuby::AtomicReference.new(:v1)
    r.set :v2
    r.get.should equal :v2
  end

  describe "#compare_and_set sets a new value if it is set to an expected value" do
    it "comparing regular objects with identity" do
      always_equal = Class.new { define_method :==, &-> _ { true } }

      v1 = always_equal.new
      bad = always_equal.new
      v2 = always_equal.new
      r = TruffleRuby::AtomicReference.new(v1)

      r.compare_and_set(bad, v2).should == false
      r.get.should equal v1

      r.compare_and_set(v1, v2).should == true
      r.get.should equal v2
    end

    it "comparing numeric objects with equality " do
      [-> { 1 },
       -> { 1.1 },
       -> { 1000 }
      ].each do |numeric_source|
        v1 = numeric_source.call
        bad = v1 + 1
        v2 = v1 + 2
        r = TruffleRuby::AtomicReference.new v1

        r.compare_and_set(bad, v2).should == false
        r.get.should equal v1

        r.compare_and_set(numeric_source.call, v2).should == true
        r.get.should equal v2
      end
    end
  end

  it "#get_and_set gets the previous value and sets a new value" do
    r = TruffleRuby::AtomicReference.new(:v1)
    r.get_and_set(:v2).should == :v1
    r.get.should == :v2
  end

  it "can be marhsalled" do
    reference = TruffleRuby::AtomicReference.new(:value)
    dumped = Marshal.dump(reference)
    loaded = Marshal.load(dumped)
    loaded.get.should == :value
  end

end
