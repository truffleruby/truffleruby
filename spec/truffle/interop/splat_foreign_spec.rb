# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

module InteropSplatFixtures

  def self.simple_method(a, b, c)
    [a, b, c]
  end

  def self.resplat_method(*x)
    [x[0], x[1], x[2]]
  end

  SIMPLE_PROC = proc { |a, b, c|
    [a, b, c]
  }

  RESPLAT_PROC = proc { |*x|
    [x[0], x[1], x[2]]
  }

  SIMPLE_LAMBDA = -> a, b, c {
    [a, b, c]
  }

  RESPLAT_LAMBDA = -> *x {
    [x[0], x[1], x[2]]
  }

end

describe "TruffleRuby can splat" do

  describe "foreign Java arrays" do

    it "in a method call" do
      InteropSplatFixtures.simple_method(*Truffle::Interop.java_array(1, 2, 3)).should == [1, 2, 3]
    end

    it "in a method call that itself splats" do
      InteropSplatFixtures.resplat_method(*Truffle::Interop.java_array(1, 2, 3)).should == [1, 2, 3]
    end

    it "in a proc call" do
      InteropSplatFixtures::SIMPLE_PROC.call(*Truffle::Interop.java_array(1, 2, 3)).should == [1, 2, 3]
    end

    it "in a proc call that itself splats" do
      InteropSplatFixtures::RESPLAT_PROC.call(*Truffle::Interop.java_array(1, 2, 3)).should == [1, 2, 3]
    end

    it "in a lambda call" do
      InteropSplatFixtures::SIMPLE_LAMBDA.call(*Truffle::Interop.java_array(1, 2, 3)).should == [1, 2, 3]
    end

    it "in a lambda call that itself splats" do
      InteropSplatFixtures::RESPLAT_LAMBDA.call(*Truffle::Interop.java_array(1, 2, 3)).should == [1, 2, 3]
    end

    it "in multiple assignment" do
      a, b, c = Truffle::Interop.java_array(1, 2, 3)
      [a, b, c].should == [1, 2, 3]
    end

    it "in multiple assignment with an explicit splat" do
      a, b, c = *Truffle::Interop.java_array(1, 2, 3)
      [a, b, c].should == [1, 2, 3]
    end

  end

end
