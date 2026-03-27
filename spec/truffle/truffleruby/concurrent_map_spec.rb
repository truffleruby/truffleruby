# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "TruffleRuby::ConcurrentMap" do
  before do
    @h = TruffleRuby::ConcurrentMap.new
  end

  it "#[] of a new instance is empty" do
    @h[:empty].should.equal? nil
  end

  it "#[]= creates a new key value pair" do
    new_value = "bar"
    @h[:foo] = new_value
    @h[:foo].should.equal? new_value
  end

  it "#compute_if_absent computes and stores new value for key if key is absent" do
    expected_value = "value for foobar"
    @h.compute_if_absent(:foobar) { expected_value }.should.equal? expected_value

    @h[:foobar].should.equal? expected_value
  end

  it "#compute_if_present computes and stores new value for key if key is present" do
    expected_value = "new value"
    @h[:foobar] = "old value"
    @h.compute_if_present(:foobar) { expected_value }.should.equal? expected_value

    @h[:foobar].should.equal? expected_value
  end

  it "#compute computes and stores new value" do
    expected_value = "new value"
    @h[:foobar] = "old value"
    @h.compute(:foobar) { expected_value }.should.equal? expected_value

    @h[:foobar].should.equal? expected_value
  end

  it "#merge_pair stores value if key is absent" do
    new_value = "bloop"
    @h.merge_pair(:foobar, new_value) do |value|
      value + new_value
    end.should.equal? new_value
    @h[:foobar].should.equal? new_value
  end

  it "#merge_pair stores computed value if key is present" do
    old_value, new_value = "bleep", "bloop"
    expected_value = old_value + new_value
    @h[:foobar] = old_value

    @h.merge_pair(:foobar, new_value) do |value|
      value + new_value
    end.should == expected_value
    @h[:foobar].should == expected_value
  end

  it "#replace_pair replaces old value with new value if key exists and current value matches old value" do
    old_value, new_value = "bleep", "bloop"
    @h[:foobar] = old_value

    @h.replace_pair(:foobar, old_value, new_value).should == true
    @h[:foobar].should.equal? new_value
  end

  guard -> { !Truffle::Boot.get_option('chaos-data') } do
    it "#replace_pair replaces the entry if the old value is a primitive" do
      one_as_long = Truffle::Debug.long(1)
      Truffle::Debug.java_class_of(one_as_long).should == 'Long'
      one_as_int = 1
      Truffle::Debug.java_class_of(one_as_int).should == 'Integer'

      @h[:foobar] = one_as_long

      @h.replace_pair(:foobar, one_as_int, 2).should == true
      @h[:foobar].should == 2
    end
  end

  it "#replace_pair doesn't replace old value if current value doesn't match old value" do
    expected_old_value = "BLOOP"
    @h[:foobar] = expected_old_value

    @h.replace_pair(:foobar, "bleep", "bloop").should == false
    @h[:foobar].should.equal? expected_old_value
  end

  it "#replace_if_exists replaces value if key exists" do
    @h[:foobar] = "bloop"
    expected_value = "bleep"

    @h.replace_if_exists(:foobar, expected_value).should == "bloop"
    @h[:foobar].should.equal? expected_value
  end

  it "#get_and_set gets current value and set new value" do
    @h.get_and_set(:a, "hello").should == nil
    @h[:a].should == "hello"

    @h[:foobar] = "bloop"
    expected_value = "bleep"

    @h.get_and_set(:foobar, expected_value).should == "bloop"
    @h[:foobar].should.equal? expected_value
  end

  it "#key? returns true if key is present" do
    @h[:foobar] = "bloop"
    @h.key?(:foobar).should == true
  end

  it "#key? returns false if key is absent" do
    @h.key?(:foobar).should == false
  end

  it "#delete deletes key and value pair" do
    value = "bloop"
    @h[:foobar] = value
    @h.delete(:foobar).should.equal? value
    @h[:foobar].should == nil
  end

  it "#delete_pair deletes pair if value equals provided value" do
    value = "bloop"
    @h[:foobar] = value
    @h.delete_pair(:foobar, value).should == true
    @h[:foobar].should == nil
  end

  guard -> { !Truffle::Boot.get_option('chaos-data') } do
    it "#delete_pair deletes pair if the old value is a primitive" do
      one_as_long = Truffle::Debug.long(1)
      Truffle::Debug.java_class_of(one_as_long).should == 'Long'
      one_as_int = 1
      Truffle::Debug.java_class_of(one_as_int).should == 'Integer'

      @h[:foobar] = one_as_long

      @h.delete_pair(:foobar, one_as_int).should == true
      @h[:foobar].should == nil
    end
  end

  it "#delete_pair doesn't delete pair if value equals provided value" do
    value = "bloop"
    @h[:foobar] = value
    @h.delete_pair(:foobar, "BLOOP").should == false
    @h[:foobar].should.equal? value
  end

  it "#clear returns an empty hash" do
    @h[:foobar] = "bleep"
    @h.clear
    @h.key?(:foobar).should == false
    @h.size.should == 0
  end

  it "#size returns the size of hash" do
    @h[:foobar], @h[:barfoo] = "bleep", "bloop"
    @h.size.should == 2
  end

  it "#get_or_default returns value of key if key mapped" do
    @h[:foobar] = "bleep"
    @h.get_or_default(:foobar, "BLEEP").should == "bleep"
    @h.key?(:foobar).should == true
  end

  it "#get_or_default returns default if key isn't mapped" do
    @h.get_or_default(:foobar, "BLEEP").should == "BLEEP"
    @h.key?(:foobar).should == false
  end

  it "#each_pair passes each key value pair to given block" do
    @h[:foobar], @h[:barfoo] = "bleep", "bloop"
    @h.each_pair do |key, value|
      value.should == @h[key]
    end
  end

  it "#each_pair returns self" do
    @h.each_pair { }.should.equal?(@h)
  end

  it "#initialize_copy creates a new instance" do
    @h.should_not.equal? @h.dup
  end
end
