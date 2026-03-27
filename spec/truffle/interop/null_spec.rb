# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.null?" do

  it "returns true for nil" do
    Truffle::Interop.null?(nil).should be_true
  end

  it "returns false for strings" do
    Truffle::Interop.null?('').should be_false
  end

  it "returns false for zero" do
    Truffle::Interop.null?(0).should be_false
  end

  it "returns false for false" do
    Truffle::Interop.null?(false).should be_false
  end

end
