# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'ffi'

describe "FFI::Struct#[]" do
  before :each do
    @struct = Class.new(FFI::Struct) do
      layout :next, :pointer,
             :name, :string
    end
  end

  describe "for a :pointer field" do
    it "returns a Pointer with address 0 if the field contains a null pointer" do
      ptr = @struct.new[:next]
      # Note that Rubinius::FFI::Struct returns nil in such a case!
      ptr.should be_kind_of(FFI::Pointer)
      ptr.address.should == 0
    end
  end

  describe "for a :string field" do
    it "reads the pointer at the field offset and then reads a string from there" do
      ptr = FFI::MemoryPointer.new(@struct.size + 8) # next + name + some safety padding

      str = FFI::MemoryPointer.from_string("Hello!")
      ptr.put_pointer(@struct.offset_of(:name), str)

      s = @struct.new(ptr)
      s[:name].should == "Hello!"
    end

    it "returns nil if there is a null pointer at the field offset" do
      @struct.new[:name].should == nil
    end
  end
end
