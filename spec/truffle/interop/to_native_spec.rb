# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.to_native" do

  it "is not supported for nil" do
    Truffle::Interop.to_native(nil)
    Truffle::Interop.pointer?(nil).should be_false
  end

  it "is not supported for objects which cannot be converted to a pointer" do
    object = Object.new
    Truffle::Interop.to_native(object)
    Truffle::Interop.pointer?(object).should be_false
  end

  it "calls #to_native does internal conversion to support as_pointer" do
    obj = Object.new

    def obj.polyglot_to_native
      @pointer = Truffle::FFI::Pointer.new(0x123)
    end

    def obj.polyglot_pointer?
      !@pointer.nil?
    end

    def obj.polyglot_as_pointer
      @pointer.address
    end

    Truffle::Interop.pointer?(obj).should be_false
    Truffle::Interop.to_native(obj)
    Truffle::Interop.pointer?(obj).should be_true
    Truffle::Interop.as_pointer(obj).should == 0x123
  end

end
