# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.instantiate" do
  it "creates new instances of objects" do
    obj = Truffle::Interop.instantiate(TruffleInteropSpecs::NewTestClass, 14, 2)
    obj.should be_an_instance_of(TruffleInteropSpecs::NewTestClass)
  end

  it "calls initialize" do
    Truffle::Interop.instantiate(TruffleInteropSpecs::NewTestClass, 14, 2).x.should == 16
  end
end
