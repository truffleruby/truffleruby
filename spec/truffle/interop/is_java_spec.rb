# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.java?" do

  it "returns false for a Ruby object" do
    Truffle::Interop.java?(Object.new).should be_false
  end

  it "returns true for a Java class" do
    Truffle::Interop.java?(Truffle::Debug.java_class).should be_true
  end

  it "returns true for a Java object" do
    Truffle::Interop.java?(Truffle::Debug.java_object).should be_true
  end

  it "returns false for some other foreign object" do
    Truffle::Interop.java?(Truffle::Debug.foreign_object).should be_false
  end

  it "returns false for primitives" do
    Truffle::Interop.java?(14).should be_false
  end

end
