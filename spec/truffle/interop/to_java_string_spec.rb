# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.to_java_string" do
  it "can be round-tripped with from_java_string with a string" do
    Truffle::Interop.from_java_string(Truffle::Interop.to_java_string("foo")).should == "foo"
  end

  it "can be round-tripped with from_java_string with a symbol" do
    Truffle::Interop.from_java_string(Truffle::Interop.to_java_string(:foo)).should == "foo"
  end

  it "raises when given a non-Symbol non-String" do
    -> { Truffle::Interop.to_java_string(14) }.should raise_error(TypeError)
    -> { Truffle::Interop.to_java_string(Object.new) }.should raise_error(TypeError)
  end
end
