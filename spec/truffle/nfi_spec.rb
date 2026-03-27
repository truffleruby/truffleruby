# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Strings going through NFI" do
  describe "via strdup() are the same byte by byte" do
    before(:all) do
      Truffle::POSIX.attach_function :strdup, [:string], :string
    end

    it "for a 7-bit String" do
      s = "abc"
      r = Truffle::POSIX.strdup(s)
      r.encoding.should equal Encoding::BINARY
      r.bytes.should == s.bytes
    end

    it "for a String with Unicode characters" do
      s = "déf"
      r = Truffle::POSIX.strdup(s)
      r.bytes.should == s.bytes
    end

    it "for a String with Japanese characters" do
      s = "こんにちは.txt"
      r = Truffle::POSIX.strdup(s)
      r.bytes.should == s.bytes
    end

    it "for a invalid UTF-8 String" do
      s = "\xa0\xa1"
      r = Truffle::POSIX.strdup(s)
      r.bytes.should == s.bytes
    end
  end
end

describe "NFI with callbacks to Ruby" do
  before :all do
    @libc = Module.new do
      Truffle::POSIX.attach_function :qsort, [:pointer, :size_t, :size_t, '(POINTER,POINTER):sint32'], :void,
                                     Truffle::POSIX::LIBC, false, :qsort, self
    end
  end

  it "sorts an array with qsort()" do
    compare_function = -> a_ptr, b_ptr {
      a = Truffle::FFI::Pointer.new(a_ptr).read_int32
      b = Truffle::FFI::Pointer.new(b_ptr).read_int32
      a <=> b
    }

    sorted = Truffle::FFI::MemoryPointer.new(:int, 4) do |array|
      array.write_array_of_int32([1, 3, 4, 2])
      @libc.qsort(array, 4, 32/8, compare_function)
      array.read_array_of_int32(4)
    end
    sorted.should == [1, 2, 3, 4]
  end

  it "propagates exceptions from the callback" do
    compare_function = -> _a, _b { raise "error in callback from native code!" }

    Truffle::FFI::MemoryPointer.new(:int, 4) do |array|
      array.write_array_of_int32([1, 3, 4, 2])
      -> {
        @libc.qsort(array, 4, 32/8, compare_function)
      }.should raise_error(RuntimeError, "error in callback from native code!")
    end
  end
end
