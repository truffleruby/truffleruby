# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.boxed?" do
  it "returns true for numbers" do
    Truffle::Interop.boxed?(1).should be_true
    Truffle::Interop.boxed?(1.2).should be_true
    Truffle::Interop.boxed?(Truffle::Debug.foreign_boxed_value(2)).should be_true
    Truffle::Interop.boxed?(Truffle::Debug.foreign_boxed_value(2.3)).should be_true
  end

  it "returns false for strings" do
    Truffle::Interop.boxed?('test').should be_false
  end

  it "returns false for symbols" do
    Truffle::Interop.boxed?(:test).should be_false
  end

  it "returns false for other objects" do
    Truffle::Interop.boxed?(Object.new).should be_false
  end
end
