# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignString" do
  before :all do
    @java_character = Truffle::Debug.java_character # 'C'
    @java_string = Truffle::Interop.as_string("abc")
    @truffle_string = Truffle::Interop.as_truffle_string("abc")
    @foreign_string = Truffle::Debug.foreign_string("abc")
    @all = [@java_character, @java_string, @truffle_string, @foreign_string]
  end

  it "are of the expected Java class" do
    Truffle::Debug.java_class_of("abc").should == "RubyString"
    Truffle::Debug.java_class_of("abc".freeze).should == "ImmutableRubyString"

    Truffle::Debug.java_class_of(Truffle::Interop.to_java_string("abc")).should == "String"
    Truffle::Debug.java_class_of(@java_character).should == "Character"
    Truffle::Debug.java_class_of(@java_string).should == "String"
    Truffle::Debug.java_class_of(@truffle_string).should == "TruffleString"
    Truffle::Debug.java_class_of(@foreign_string).should == "ForeignString"
  end

  it "are not boxed" do
    Truffle::Interop.boxed?(@foreign_string).should be_false
  end

  it "are converted to Ruby automatically on the LHS of string concatenation" do
    a = Truffle::Debug.foreign_string('a')
    b = 'b'
    (a + b).should == 'ab'
  end

  it "are converted to Ruby automatically on the RHS of string concatenation" do
    a = 'a'
    b = Truffle::Debug.foreign_string('b')
    (a + b).should == 'ab'
  end

  it "compare == in any order" do
    ruby_string = "abc"
    all_strings = [ruby_string, @java_string, @truffle_string, @foreign_string]
    all_strings.each do |s1|
      all_strings.each do |s2|
        (s1 == s2).should.equal? true
        s1.should == s2
      end
    end
  end

  it "respond to #to_s" do
    @java_character.to_s.should == 'C'
    @java_string.to_s.should == 'abc'
    @truffle_string.to_s.should == 'abc'
    @foreign_string.to_s.should == 'abc'
  end

  it "respond to #to_str" do
    @java_character.to_str.should == 'C'
    @java_string.to_str.should == 'abc'
    @truffle_string.to_str.should == 'abc'
    @foreign_string.to_str.should == 'abc'
  end

  it "can be printed with #puts" do
    -> {
      puts Truffle::Debug.foreign_string('a')
    }.should output_to_fd("a\n")
  end

  it "can be printed as if a Ruby string with #p" do
    -> {
      p Truffle::Debug.foreign_string('a')
    }.should output_to_fd("\"a\"\n")
  end

  it "are frozen" do
    @java_character.should.frozen?
    @java_string.should.frozen?
    @truffle_string.should.frozen?
    @foreign_string.should.frozen?
  end

  it "cannot be mutated" do
    -> { @java_character.capitalize! }.should raise_error(FrozenError)
    -> { @java_string.capitalize! }.should raise_error(FrozenError)
    -> { @truffle_string.capitalize! }.should raise_error(FrozenError)
    -> { @foreign_string.capitalize! }.should raise_error(FrozenError)

    @java_character.should == "C"
    @java_string.should == "abc"
    @truffle_string.should == "abc"
    @foreign_string.should == "abc"
  end

  it "supports #+@ and then mutation" do
    @all.each do |foreign_string|
      copy = +foreign_string
      copy << "."
      copy.should == "#{foreign_string}."
    end
  end

  it "supports #dup and then mutation" do
    @all.each do |foreign_string|
      copy = foreign_string.dup
      copy << "."
      copy.should == "#{foreign_string}."
    end
  end

  it "supports #clone and then mutation" do
    @all.each do |foreign_string|
      foreign_string.clone.should.frozen?
      foreign_string.clone(freeze: true).should.frozen?

      copy = foreign_string.clone(freeze: false)
      copy << "."
      copy.should == "#{foreign_string}."
    end
  end

  it "has all Ruby String methods" do
    @java_character.should == "C"
    @java_string.capitalize.should == "Abc"
    @truffle_string.capitalize.should == "Abc"
    @foreign_string.capitalize.should == "Abc"

    Truffle::Debug.foreign_string('a bb ccc').split.should == ["a", "bb", "ccc"]
  end
end
