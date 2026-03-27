# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.unbox" do
  it "passes through fixnums" do
    Truffle::Interop.unbox(14).should == 14
  end

  it "passes through floats" do
    Truffle::Interop.unbox(14.2).should == 14.2
  end

  it "passes through true" do
    Truffle::Interop.unbox(true).should == true
  end

  it "passes through false" do
    Truffle::Interop.unbox(false).should == false
  end

  it "is not supported for a Ruby String" do
    -> { Truffle::Interop.unbox('test') }.should raise_error(ArgumentError)
  end

  it "is not supported for a Ruby Symbol" do
    -> { Truffle::Interop.unbox(:test) }.should raise_error(ArgumentError)
  end

  it "is not supported for nil" do
    -> { Truffle::Interop.unbox(nil) }.should raise_error(ArgumentError)
  end

  it "is not supported for objects which cannot be unboxed" do
    -> { Truffle::Interop.unbox(Object.new) }.should raise_error(ArgumentError)
  end
end
