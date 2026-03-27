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

describe "Truffle::Interop.as_pointer" do

  it "is not supported for nil" do
    -> { Truffle::Interop.as_pointer(nil) }.should raise_error(Polyglot::UnsupportedMessageError)
  end

  it "is not supported for objects which cannot be converted to a pointer" do
    -> { Truffle::Interop.as_pointer(Object.new) }.should raise_error(Polyglot::UnsupportedMessageError)
  end

  it "works on Truffle::FFI::Pointer" do
    Truffle::Interop.as_pointer(Truffle::FFI::Pointer.new(0x123)).should == 0x123
  end

  it "calls #address" do
    Truffle::Interop.as_pointer(TruffleInteropSpecs::AsPointerClass.new).should == 0x123
  end

end
