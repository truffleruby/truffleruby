# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

module Truffle::FFI
  class AbstractMemory
  end

  class Pointer < AbstractMemory
    # Indicates how many bytes the type that the pointer is cast as uses.
    attr_accessor :type_size

    # NOTE: redefined in lib/truffle/ffi.rb for full FFI
    def self.find_type_size(type)
      Primitive.pointer_find_type_size(type)
    end

    def initialize(type = nil, address)
      if Truffle::Interop.pointer?(address)
        address = Truffle::Interop.as_pointer(address)
      end
      self.address = address

      @type_size = case type
                   when nil
                     1
                   when Integer
                     type
                   when Symbol
                     Pointer.find_type_size(type)
                   else
                     if defined?(::FFI)
                       type.size
                     else
                       raise ArgumentError, "incorrect pointer type: #{type.inspect}"
                     end
                   end
    end

    def initialize_copy(from)
      total = Primitive.pointer_size(from)
      raise RuntimeError, 'cannot duplicate unbounded memory area' unless total != UNBOUNDED
      Primitive.pointer_malloc self, total
      Primitive.pointer_copy_memory address, from.address, total
      self
    end

    def size
      Primitive.pointer_size(self)
    end
    alias_method :total, :size

    def clear
      raise RuntimeError, 'cannot clear unbounded memory area' unless Primitive.pointer_size(self) != UNBOUNDED
      Primitive.pointer_clear self, Primitive.pointer_size(self)
    end

    def inspect
      # Don't have this print the data at the location. It can crash everything.
      addr = address()

      if addr < 0
        sign = '-'
        addr = -addr
      else
        sign = ''
      end

      size = Primitive.pointer_size(self)
      if size != UNBOUNDED
        "#<#{Primitive.class(self).name} address=#{sign}0x#{addr.to_s(16)} size=#{size}>"
      else
        "#<#{Primitive.class(self).name} address=#{sign}0x#{addr.to_s(16)}>"
      end
    end

    def null?
      address == 0x0
    end

    def +(offset)
      ptr = Pointer.new(address + offset)
      if Primitive.pointer_size(self) != UNBOUNDED
        ptr.total = Primitive.pointer_size(self) - offset
      end
      ptr
    end

    def slice(offset, length)
      ptr = Pointer.new(address + offset)
      ptr.total = length
      ptr
    end

    def [](idx)
      raise ArgumentError, 'unknown type size' unless @type_size
      self + (idx * @type_size)
    end

    def ==(other)
      return true if Primitive.nil?(other) && null?
      return false unless Primitive.is_a?(other, Pointer)
      address == other.address
    end

    def network_order(start, size)
      raise 'FFI::Pointer#network_order not yet implemented'
    end

    private def check_bounds(offset, length)
      size = Primitive.pointer_size(self)
      if offset < 0 || offset + length > size
        raise IndexError, "Memory access offset=#{offset} size=#{length} is out of bounds"
      end
    end

    def get_string(offset, length = nil)
      Primitive.pointer_read_string_to_null address + offset, length || size
    end

    def put_string(offset, str)
      put_bytes(offset, str)
      put_char(offset + str.bytesize, 0)
    end

    def get_array_of_string(offset, count = nil)
      if count
        check_bounds(offset, count * SIZE)
        Array.new(count) do |i|
          ptr = get_pointer(offset + i * SIZE)
          if ptr.null?
            nil
          else
            ptr.read_string
          end
        end
      else
        check_bounds(offset, SIZE)
        strings = []
        i = 0
        loop do
          ptr = get_pointer(offset + i * SIZE)
          if ptr.null?
            return strings
          else
            strings << ptr.read_string
          end
          i += 1
        end
      end
    end

    def read_array_of_string(count = nil)
      get_array_of_string(0, count)
    end

    def get_bytes(offset, length)
      check_bounds(offset, length)
      Primitive.pointer_read_bytes address + offset, length
    end

    def put_bytes(offset, str, index = 0, length = nil)
      raise RangeError, 'index cannot be less than zero' if index < 0
      if length
        if index + length > str.bytesize
          raise RangeError, 'index+length is greater than size of string'
        end
      else
        if index > str.bytesize
          raise IndexError, 'index is greater than size of string'
        end
        length = str.bytesize - index
      end
      check_bounds(offset, length)
      Primitive.pointer_write_bytes address + offset, str, index, length
      self
    end

    def read_bytes(length)
      get_bytes(0, length)
    end

    def write_bytes(str, index = 0, length = nil)
      put_bytes(0, str, index, length)
    end

    def __copy_from__(pointer, size)
      Primitive.pointer_copy_memory address, pointer.address, size
    end

    def get(type, offset)
      begin
        type = ::FFI.find_type(type)
      rescue TypeError => e
        raise ArgumentError, e.message
      end
      type.get_at(self, offset)
    end

    def put(type, offset, value)
      begin
        type = ::FFI.find_type(type)
      rescue TypeError => e
        raise ArgumentError, e.message
      end
      type.put_at(self, offset, value)
    end

    SIZE = 8

    Truffle::Boot.delay do
      if Truffle::Boot.get_option 'platform-native'
        NULL = Pointer.new(0x0)
      end
    end
  end

  class MemoryPointer < Pointer
    def initialize(type, count = 1, clear = true)
      super(type, 0)
      total = @type_size * (count || 1)

      Primitive.pointer_malloc self, total
      Primitive.pointer_clear self, total if clear
    end

    def self.new(type, count = 1, clear = true)
      ptr = super(type, count, clear)

      if block_given?
        begin
          yield ptr
        ensure
          ptr.free
        end
      else
        ptr.autorelease = true
        ptr
      end
    end

    def self.from_string(str)
      str = Primitive.convert_with_to_str(str)
      ptr = new(1, str.bytesize + 1, false)
      ptr.put_string(0, str)
      ptr
    end
  end

  class Pool
    # Use Primitive.io_thread_buffer_allocate(Primitive.pointer_find_type_size(:type) * n)
    # instead for a single pointer.
    # This method always returns an Array of 1 + args.size FFI::Pointer, the first pointer is the full buffer to pass to #stack_free,
    # followed by args.size pointers, one for each argument.
    def self.stack_alloc(*args)
      total_length = 0
      offsets = []
      args.each do |length|
        unless Primitive.is_a?(length, Integer)
          raise ArgumentError, "incorrect pointer type: #{length.inspect}"
        end
        offsets << [total_length, length]
        total_length += length
      end
      buffer = Primitive.io_thread_buffer_allocate(total_length)
      [buffer, *offsets.map { |offset, length| buffer.slice(offset, length) }]
    end

    # The argument is the first pointer (typically named buffer) returned by #stack_alloc.
    # It needs to be a FFI::Pointer with the full allocated size, so the primitive knows how much to free.
    def self.stack_free(buffer)
      Primitive.io_thread_buffer_free(buffer)
    end
  end
end
