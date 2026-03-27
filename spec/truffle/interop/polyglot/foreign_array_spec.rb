# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignArray" do
  before :each do
    @foreign = Truffle::Interop.to_java_array([1, 2, 3])
    @empty = Truffle::Interop.to_java_array([])
  end

  it "compares #== if elements are #==" do
    foreign = Truffle::Interop.to_java_array([
      Truffle::Debug.foreign_string("one"),
      Truffle::Interop.as_string("two"),
      Truffle::Interop.as_truffle_string("three")])
    ruby = ["one", "two", "three"]

    foreign.should == ruby
    ruby.should == foreign
  end

  it "supports #length and #size" do
    @foreign.size.should == 3
    @foreign.length.should == 3
  end

  it "should index array with #[]" do
    @foreign[0].should == 1
    @foreign[1].should == 2
    @foreign[2].should == 3
    @foreign[-1].should == 3
    @foreign[-3].should == 1
    @foreign[3].should == nil
    @foreign[-4].should == nil
  end

  it "should index array with #at" do
    @foreign.at(0).should == 1
    @foreign.at(1).should == 2
    @foreign.at(2).should == 3
    @foreign.at(-1).should == 3
    @foreign.at(-3).should == 1
    @foreign.at(3).should == nil
    @foreign.at(-4).should == nil
  end

  it "should access the first element with #first" do
    @foreign.first.should == 1
    @empty.first.should == nil
  end

  it "should access the last element with #last" do
    @foreign.last.should == 3
    @empty.last.should == nil
  end

  it "supports #each" do
    index = 0
    @foreign.each do |val|
      val.should == (index += 1)
    end
  end

  it "supports #each_with_index" do
    @foreign.each_with_index do |val, index|
      val.should == (index + 1)
    end
  end

  it "returns an Enumerator for #each with no block" do
    enum = @foreign.each
    enum.class.should == Enumerator
    enum.each_with_index do |val, index|
      @foreign[index].should == val
    end
  end

  it "supports #take" do
    slice = @foreign.take(2)
    slice[0].should == 1
    slice[1].should == 2
    slice.at(2).should == nil
  end

  it "should allow assignment of array element with #[]=" do
    @foreign[2] = 10
    @foreign[2].should == 10
  end

  it "should allow the use of #next" do
    enum = @foreign.each
    enum.class.should == Enumerator
    enum.next.should == 1
  end

  it "supports #count" do
    foreign = Truffle::Interop.to_java_array([1, 2, 4, 2])
    foreign.count.should == 4
    foreign.count(2).should == 2
    foreign.count { |x| x % 2 == 0 }.should == 3
  end
end

describe "Foreign arrays" do
  it "can be printed with #print" do
    foreign = Truffle::Interop.to_java_array([1, 2, 3])
    -> {
      print foreign
    }.should output_to_fd(foreign.to_s)
  end

  it "can be printed with #puts" do
    -> {
      puts Truffle::Interop.to_java_array([1, 2, 3])
    }.should output_to_fd("1\n2\n3\n")
  end

  it "can be printed with #p" do
    foreign = Truffle::Interop.to_java_array([1, 2, 3])
    -> {
      p foreign
    }.should output_to_fd("#{foreign.inspect}\n")
  end
end

describe "Foreign arrays that are also pointers" do
  it "can be printed with #print" do
    foreign = Truffle::Debug.foreign_pointer_array
    -> {
      print foreign
    }.should output_to_fd(foreign.to_s)
  end

  it "can be printed with #puts" do
    -> {
      puts Truffle::Debug.foreign_pointer_array
    }.should output_to_fd("1\n2\n3\n")
  end

  it "can be printed with #p" do
    foreign = Truffle::Debug.foreign_pointer_array
    -> {
      p foreign
    }.should output_to_fd("#{foreign.inspect}\n")
  end
end
