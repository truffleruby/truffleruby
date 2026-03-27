# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.java_string?" do

  it "returns false for a Ruby String" do
    Truffle::Interop.java_string?("foo").should be_false
  end

  it "returns true for a Java String" do
    Truffle::Interop.java_string?(Truffle::Interop.to_java_string("foo")).should be_true
  end

  it "returns false for other objects" do
    Truffle::Interop.java_string?(Object.new).should be_false
  end

end
