# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Array#[]=" do
  def storage(ary)
    Truffle::Debug.array_storage(ary)
  end

  before :each do
    @long = 1 << 52
  end

  guard -> { !Truffle::Boot.get_option('chaos-data') } do
    it "migrates to the most specific storage if initially empty" do
      ary = []
      ary[0] = Object.new
      storage(ary).should == "Object[]"

      ary = []
      ary[0] = 3.14
      storage(ary).should == "double[]"

      ary = []
      ary[0] = @long
      storage(ary).should == "long[]"

      ary = []
      ary[0] = 1
      storage(ary).should == "int[]"
    end

    it "keeps the same storage if compatible and in bounds" do
      ary = [Object.new, Object.new]
      ary[1] = Object.new
      storage(ary).should == "Object[]"

      ary = [3.11, 3.12]
      ary[1] = 3.14
      storage(ary).should == "double[]"

      ary = [@long-2, @long-1]
      ary[1] = @long
      storage(ary).should == "long[]"

      ary = [@long-2, @long-1]
      ary[1] = 3
      storage(ary).should == "long[]"

      ary = [0, 1]
      ary[1] = 2
      storage(ary).should == "int[]"
    end

    it "generalizes if not compatible and in bounds" do
      ary = [3.11, 3.12]
      ary[1] = Object.new
      storage(ary).should == "Object[]"

      ary = [@long-2, @long-1]
      ary[1] = 3.14
      storage(ary).should == "Object[]"

      ary = [0, 1]
      ary[1] = @long
      storage(ary).should == "long[]"

      ary = [0, 1]
      ary[1] = Object.new
      storage(ary).should == "Object[]"
    end

    it "keeps the same storage if compatible and appending" do
      ary = [Object.new, Object.new]
      ary[2] = Object.new
      storage(ary).should == "Object[]"

      ary = [3.11, 3.12]
      ary[2] = 3.14
      storage(ary).should == "double[]"

      ary = [@long-2, @long-1]
      ary[2] = @long
      storage(ary).should == "long[]"

      ary = [0, 1]
      ary[2] = 2
      storage(ary).should == "int[]"
    end
  end

  it "migrates to Object[] if writing out of bounds" do
    ary = [Object.new, Object.new]
    ary[3] = Object.new
    storage(ary).should == "Object[]"

    ary = [3.11, 3.12]
    ary[3] = 3.14
    storage(ary).should == "Object[]"

    ary = [@long-2, @long-1]
    ary[3] = @long
    storage(ary).should == "Object[]"

    ary = [0, 1]
    ary[3] = 2
    storage(ary).should == "Object[]"
  end
end
