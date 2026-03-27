# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.instantiable?" do

  it "returns true for objects that can be instantiated" do
    Truffle::Interop.instantiable?(Object).should be_true
  end

  it "returns false for objects that cannot be instantiated" do
    Truffle::Interop.instantiable?(14).should be_false
  end

end
