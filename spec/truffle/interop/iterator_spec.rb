# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop iterator messages" do
  def should_have_iterator(obj, expected_values)
    Truffle::Interop.should.has_iterator?(obj)

    iterator = Truffle::Interop.iterator(obj)
    Truffle::Interop.should.iterator?(iterator)
    expected_values.each do |value|
      Truffle::Interop.has_iterator_next_element?(iterator).should == true
      Truffle::Interop.iterator_next_element(iterator).should == value
    end
    Truffle::Interop.has_iterator_next_element?(iterator).should == false
    -> { Truffle::Interop.iterator_next_element(iterator) }.should raise_error(StopIteration)

    iterator = Truffle::Interop.iterator(obj)
    Truffle::Interop.should.iterator?(iterator)
    expected_values.each do |value|
      Truffle::Interop.iterator_next_element(iterator).should == value
    end
    -> { Truffle::Interop.iterator_next_element(iterator) }.should raise_error(StopIteration)
  end

  it "allow to iterate an array" do
    should_have_iterator([1, 2], [1, 2])
  end

  it "allows iterating a range" do
    should_have_iterator(1..3, [1, 2, 3])
  end

  it "allows iterating a hash" do
    should_have_iterator({ 1 => "one", 2 => "two"}, [[1, "one"], [2, "two"]])
  end

  it "allows iterating user defined classes" do
    cls = Class.new do
      include Enumerable
      def each
        yield 1
        yield 2
      end
    end

    obj = cls.new
    should_have_iterator(obj, [1, 2])
  end
end
