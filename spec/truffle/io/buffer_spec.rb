# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.
#

require_relative '../../ruby/spec_helper'

describe "IO buffers" do

  before do
    @mem_check = Proc.new do |*ptrs|
      sequence = "\xde\xad\xbe\xef\x53"
      ptrs.each do |ptr|
        # We want to repeat the byte sequence enough times to be just larger than the size of the buffer.
        bytes = sequence * ((ptr.size + sequence.bytesize - 1) / sequence.bytesize)
        ptr.write_bytes(bytes , 0, ptr.size)
      end
      ptrs.each do |ptr|
        # We want to repeat the byte sequence enough times to be just larger than the size of the buffer.
        bytes = sequence * ((ptr.size + sequence.bytesize - 1) / sequence.bytesize)
        ptr.read_bytes(ptr.size).bytes.should == bytes.bytes[0...ptr.size]
      end
    end
  end

  it "allocate buffer space and return a non-null pointer" do
    ptr = Primitive.io_thread_buffer_allocate(1)
    begin
      ptr.null?.should == false
      @mem_check.call(ptr)
    ensure
      Primitive.io_thread_buffer_free(ptr)
    end
  end

  it "allocate buffer space on a new thread and return a non-null pointer" do
    Thread.new do
      ptr = Primitive.io_thread_buffer_allocate(1)
      begin
        ptr.null?.should == false
        @mem_check.call(ptr)
      ensure
        Primitive.io_thread_buffer_free(ptr)
      end
    end.join
  end

  it "allocate multiple buffers and returns non-null pointers" do
    ptr1 = Primitive.io_thread_buffer_allocate(1)
    ptr2 = Primitive.io_thread_buffer_allocate(1)
    begin
      ptr1.null?.should == false
      ptr2.null?.should == false
      @mem_check.call(ptr1, ptr2)
    ensure
      Primitive.io_thread_buffer_free(ptr2)
      Primitive.io_thread_buffer_free(ptr1)
    end
  end

  it "allocate multiple buffers and returns non-null pointers for large buffer sizes" do
    ptr1 = Primitive.io_thread_buffer_allocate(4096 * 4)
    ptr2 = Primitive.io_thread_buffer_allocate(4096 * 4)
    begin
      ptr1.null?.should == false
      ptr2.null?.should == false
      @mem_check.call(ptr1, ptr2)
    ensure
      Primitive.io_thread_buffer_free(ptr2)
      Primitive.io_thread_buffer_free(ptr1)
    end
  end

  it "allocate multiple buffers on a new thread and returns non-null pointers" do
    Thread.new do
      ptr1 = Primitive.io_thread_buffer_allocate(1)
      ptr2 = Primitive.io_thread_buffer_allocate(1)
      begin
        ptr1.null?.should == false
        ptr2.null?.should == false
        @mem_check.call(ptr1, ptr2)
      ensure
        Primitive.io_thread_buffer_free(ptr2)
        Primitive.io_thread_buffer_free(ptr1)
      end
    end.join
  end

  it "allocate multiple buffers on a new thread and returns non-null pointers for large buffer sizes" do
    Thread.new do
      ptr1 = Primitive.io_thread_buffer_allocate(4096 * 4)
      ptr2 = Primitive.io_thread_buffer_allocate(4096 * 4)
      begin
        ptr1.null?.should == false
        ptr2.null?.should == false
        @mem_check.call(ptr1, ptr2)
      ensure
        Primitive.io_thread_buffer_free(ptr2)
        Primitive.io_thread_buffer_free(ptr1)
      end
    end.join
  end

  it "return a non-null pointer when 0 bytes of buffer are requested" do
    ptr = Primitive.io_thread_buffer_allocate(1)
    begin
      ptr.null?.should == false

    ensure
      Primitive.io_thread_buffer_free(ptr)
    end
  end

  it "return a non-null pointer on a new thread when 0 bytes of buffer are requested" do
    Thread.new do
      ptr = Primitive.io_thread_buffer_allocate(1)
      begin
        ptr.null?.should == false

      ensure
        Primitive.io_thread_buffer_free(ptr)
      end
    end.join
  end

end
