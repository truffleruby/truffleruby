# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.has_members?" do

  it "returns true for an array" do
    Truffle::Interop.has_members?([1, 2, 3]).should be_true
  end

  it "returns true for a hash" do
    Truffle::Interop.has_members?({a: 1, b: 2, c: 3}).should be_true
  end

  it "returns true for general objects" do
    Truffle::Interop.has_members?(Object.new).should be_true
  end

  it "returns true for frozen objects" do
    Truffle::Interop.has_members?(Object.new.freeze).should be_true
  end

  it "returns false for true" do
    Truffle::Interop.has_members?(true).should be_false
  end

  it "returns false for false" do
    Truffle::Interop.has_members?(false).should be_false
  end

  it "returns false for Fixnum" do
    Truffle::Interop.has_members?(14).should be_false
  end

  it "returns true for Bignum" do
    Truffle::Interop.has_members?(bignum_value).should be_true
  end

  it "returns false for Float" do
    Truffle::Interop.has_members?(14.2).should be_false
  end

  it "returns true for Symbol" do
    Truffle::Interop.has_members?(:foo).should be_true
  end

end
