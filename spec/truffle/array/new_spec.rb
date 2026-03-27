# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Array.new" do
  def storage(ary)
    Truffle::Debug.array_storage(ary)
  end

  def capacity(ary)
    Truffle::Debug.array_capacity(ary)
  end

  before :each do
    @long = 1 << 52
  end

  it "creates arrays with the requested capacity" do
    a = Array.new(17)
    capacity(a).should == 17
  end

  it "creates arrays with the requested capacity with a default value" do
    a = Array.new(17, 1)
    capacity(a).should == 17
  end

  it "creates arrays with the requested capacity with a value block" do
    a = Array.new(17) { 1 }
    capacity(a).should == 17
  end
end
