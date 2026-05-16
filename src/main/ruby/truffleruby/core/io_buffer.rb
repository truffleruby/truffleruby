# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

class IO::Buffer

  Truffle::Boot.delay do
    PAGE_SIZE = Truffle::POSIX.truffleposix_page_size

    if from_env = ENV['RUBY_IO_BUFFER_DEFAULT_SIZE'] and from_env = Integer(from_env) and from_env > 0
      default_size = from_env
    elsif 65536 < PAGE_SIZE
      default_size = PAGE_SIZE
    else
      default_size = 65536
    end
    DEFAULT_SIZE = default_size
  end

  # From lib/cext/include/ruby/io/buffer.h:

  # The memory in the buffer is owned by someone else.
  # More specifically, it means that someone else owns the buffer and we shouldn't try to resize it.
  EXTERNAL = 1
  # The memory in the buffer is allocated internally.
  INTERNAL = 2
  # The memory in the buffer is mapped.
  # A non-private mapping is marked as external.
  MAPPED = 4

  # A mapped buffer that is also shared.
  SHARED = 8

  # The buffer is locked and cannot be resized.
  # More specifically, it means we can't change the base address or size.
  # A buffer is typically locked before a system call that uses the data.
  LOCKED = 32

  # The buffer mapping is private and will not impact other processes or the underlying file.
  PRIVATE = 64

  # The buffer is read-only and cannot be modified.
  READONLY = 128

  # The buffer is backed by a file.
  FILE = 256

  # TODO: could be more direct by reading the RubyString#locked field
  STRING_LOCKED = 512
  private_constant :STRING_LOCKED

  SLICE = 1024
  private_constant :SLICE

  NULL = ''.dup.freeze # A unique empty frozen String
  private_constant :NULL

  PROT_READ = Truffle::Config['platform.mmap.PROT_READ']
  PROT_WRITE = Truffle::Config['platform.mmap.PROT_WRITE']
  MAP_SHARED = Truffle::Config['platform.mmap.MAP_SHARED']
  MAP_PRIVATE = Truffle::Config['platform.mmap.MAP_PRIVATE']
  private_constant :PROT_READ, :PROT_WRITE, :MAP_SHARED, :MAP_PRIVATE

  class AccessError < RuntimeError
  end

  class LockedError < RuntimeError
  end

  class InvalidatedError < RuntimeError
  end

  class AllocationError < RuntimeError
  end

  def self.map(file, size = nil, offset = 0, flags = 0)
    Truffle::Type.rb_check_type(file, ::File)

    read_only = flags.anybits?(READONLY)
    private = flags.anybits?(PRIVATE)
    flags |= (FILE | MAPPED)
    flags |= (EXTERNAL | SHARED) unless private

    fd = file.fileno

    offset = Primitive.convert_with_to_int(offset)
    raise ArgumentError, "Offset can't be negative!" if offset < 0

    file_size = file.size
    if size
      raise TypeError, 'not an Integer' unless Primitive.is_a?(size, Integer)
      raise ArgumentError, "Size can't be negative!" if size < 0
      raise ArgumentError, "Size can't be zero!" if size == 0
      raise ArgumentError, "Size can't be larger than file size!" if size > file_size
    else
      size = file_size - offset
    end
    raise ArgumentError, 'Invalid negative or zero file size!' if size <= 0
    raise ArgumentError, 'Offset too large!' if offset + size > file_size

    pointer = Truffle::POSIX.mmap(nil, size, read_only ? PROT_READ : PROT_WRITE, private ? MAP_PRIVATE : MAP_SHARED, fd, offset)
    Errno.handle if pointer.address <= 0
    string = Truffle::CExt.rb_tr_static_native_string(pointer.address, size, Encoding::BINARY)
    # TODO: finalizer/free
    new(string, flags)
  end

  def self.for(string)
    frozen = Primitive.frozen?(string)
    if block_given?
      flags = EXTERNAL | SLICE | (frozen ? READONLY : STRING_LOCKED)
      buffer = new(string, flags)
      Truffle::CExt.rb_str_locktmp(string) unless frozen
      begin
        yield buffer
      ensure
        Truffle::CExt.rb_str_unlocktmp(string) unless frozen
        buffer.free
      end
    else
      frozen_copy = frozen ? string : string.dup.freeze
      new(frozen_copy, EXTERNAL | SLICE | READONLY)
    end
  end

  def self.string(length)
    raise LocalJumpError, 'no block given' unless block_given?
    raise ArgumentError, 'negative string size (or size too big)' if length < 0
    string = "\x00".b * length
    buffer = new(string, EXTERNAL)
    begin
      yield buffer
      # TODO we do need to honor resizes/reassign to @string ?
      # string = Primitive.object_ivar_get(buffer, :@string)
    ensure
      buffer.free
    end
    string
  end

  def initialize(size = DEFAULT_SIZE, flags = undefined)
    if Primitive.is_a?(size, String)
      @string = size
      @flags = flags
    elsif Primitive.is_a?(size, Integer)
      raise ArgumentError, "Size can't be negative!" if size < 0

      if Primitive.undefined?(flags)
        flags = size >= PAGE_SIZE ? MAPPED : INTERNAL
      else
        raise TypeError, 'not an Integer' unless Primitive.is_a?(flags, Integer)
        raise ArgumentError, "Flags can't be negative!" if flags < 0
        raise AllocationError, 'Could not allocate buffer!' if flags.nobits?(INTERNAL | MAPPED)
      end

      @string = size == 0 ? NULL : "\x00".b * size
      flags = 0 if size == 0
      @flags = flags
    else
      raise TypeError, 'not an Integer'
    end

    # only set for slices
    @parent = nil
    @offset = 0
  end

  def external? = @flags.anybits?(EXTERNAL)
  def internal? = @flags.anybits?(INTERNAL)
  def mapped? = @flags.anybits?(MAPPED)
  def shared? = @flags.anybits?(SHARED)
  def locked? = @flags.anybits?(LOCKED)
  def private? = @flags.anybits?(PRIVATE)
  def readonly? = @flags.anybits?(READONLY)

  def empty? = @string.empty?
  def size = @string.bytesize

  def null? = Primitive.equal?(@string, NULL)

  def valid?
    if @parent
      @offset + size <= @parent.size
    else
      true
    end
  end

  def to_s
    s = "#<#{Primitive.class(self)} 0x????????????????+#{size}"
    s += ' EXTERNAL' if external?
    s += ' INTERNAL' if internal?
    s += ' MAPPED' if mapped?
    s += ' SHARED' if shared?
    s += ' LOCKED' if locked?
    s += ' PRIVATE' if private?
    s += ' READONLY' if readonly?
    s += ' SLICE' if @flags.anybits?(SLICE)
    s += ' NULL' if null?
    "#{s}>"
  end

  def inspect
    s = to_s
    offset = 0
    @string.bytes.each_slice(16).each do |slice|
      l = '0x%08x  ' % offset
      slice.each do |byte|
        l += '%02x ' % byte
      end
      l = l.ljust(60)
      slice.each do |byte|
        if Primitive.character_printable?(byte, Encoding::US_ASCII)
          l += byte.chr
        else
          l += '.'
        end
      end
      s += "\n#{l}"
      offset += 16
    end
    s
  end

  def free
    raise LockedError, 'Buffer is locked!' if locked?
    # TODO actually free if owned
    @string = NULL
    @flags = 0
    self
  end

  def slice(offset = 0, length = self.size - offset)
    # TODO must be able to write through slice
    # TODO must avoid copy for native strings too
    string_slice = null? ? NULL : @string.byteslice(offset, length)
    slice = IO::Buffer.new(string_slice, 0)

    if @parent
      root = @parent
      root_offset = @offset + offset
    else
      root = self
      root_offset = offset
    end
    Primitive.object_ivar_set(slice, :@parent, root)
    Primitive.object_ivar_set(slice, :@offset, root_offset)

    slice
  end

  def resize(new_size)
    raise TypeError, 'not an Integer' unless Primitive.is_a?(new_size, Integer)
    raise ArgumentError, "Size can't be negative!" if new_size < 0

    raise LockedError, 'Cannot resize locked buffer!' if locked?
    raise AccessError, 'Cannot resize external buffer!' if external?

    if new_size == 0
      free
    elsif new_size == size
      # OK
    elsif new_size < size
      @string = @string.byteslice(0, new_size)
    else
      was_null = null?
      @string += "\x00".b * (new_size - size)
      if was_null
        @flags |= (size >= PAGE_SIZE ? MAPPED : INTERNAL)
      end
    end
    self
  end

  def locked
    raise LockedError, 'Buffer already locked!' if locked?
    @flags |= LOCKED
    begin
      yield
    ensure
      @flags &= ~LOCKED
    end
  end

  def transfer
    raise LockedError, 'Cannot transfer ownership of locked buffer!' if locked?

    copy = self.dup
    @string = NULL
    copy
  end

  def each_byte(offset = 0, count = size, &block)
    @string.byteslice(offset, count).each_byte(&block)
  end

  def each(buffer_type, offset = 0, count = size)
    return to_enum(:each, buffer_type, offset, count) unless block_given?

    case buffer_type
    when :U8
      each_byte(offset, count).with_index(offset) do |byte, byte_offset|
        yield byte_offset, byte
      end
    else
      raise buffer_type
    end
  end

  def get_value(buffer_type, offset)
    case buffer_type
    when :U8
      @string.getbyte(offset)
    else
      raise buffer_type
    end
  end

  def get_string(offset = 0, length = self.size, encoding = Encoding::BINARY)
    raise InvalidatedError, 'Buffer has been invalidated!' unless valid?

    @string.byteslice(offset, length).force_encoding(encoding)
  end

  def set_string(string, offset = 0, source_length = string.bytesize, source_offset = 0)
    raise AccessError, 'Buffer is not writable!' if readonly?
    unlocked do
      # bytesplice can change the encoding so we have to restore it
      enc = @string.encoding
      @string.bytesplice(offset, source_length, string, source_offset, source_length)
      @string.force_encoding(enc)
    end
    self
  end

  def ~
    IO::Buffer.new(@string.bytes.map do |byte|
      ~byte
    end.pack('C*'), INTERNAL)
  end

  def &(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    mask_size = mask.size
    i = 0
    IO::Buffer.new(@string.bytes.map do |byte|
      value = byte & mask.get_value(:U8, i)
      i += 1
      i = 0 if i == mask_size
      value
    end.pack('C*'), INTERNAL)
  end

  def |(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    mask_size = mask.size
    i = 0
    IO::Buffer.new(@string.bytes.map do |byte|
      value = byte | mask.get_value(:U8, i)
      i += 1
      i = 0 if i == mask_size
      value
    end.pack('C*'), INTERNAL)
  end

  def ^(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    mask_size = mask.size
    i = 0
    IO::Buffer.new(@string.bytes.map do |byte|
      value = byte ^ mask.get_value(:U8, i)
      i += 1
      i = 0 if i == mask_size
      value
    end.pack('C*'), INTERNAL)
  end

  def not!
    unlocked do
      self_size = self.size
      i = 0
      while i < self_size
        @string.setbyte(i, ~@string.getbyte(i))
        i += 1
      end
    end
    self
  end

  def and!(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    unlocked do
      self_size = self.size
      mask_size = mask.size
      i = 0
      while i < self_size
        @string.setbyte(i, @string.getbyte(i) & mask.get_value(:U8, i % mask_size))
        i += 1
      end
    end
    self
  end

  def or!(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    unlocked do
      self_size = self.size
      mask_size = mask.size
      i = 0
      while i < self_size
        @string.setbyte(i, @string.getbyte(i) | mask.get_value(:U8, i % mask_size))
        i += 1
      end
    end
    self
  end

  def xor!(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    unlocked do
      self_size = self.size
      mask_size = mask.size
      i = 0
      while i < self_size
        @string.setbyte(i, @string.getbyte(i) ^ mask.get_value(:U8, i % mask_size))
        i += 1
      end
    end
    self
  end

  # TODO: tmp hack
  private def unlocked
    if @flags.anybits?(STRING_LOCKED)
      Truffle::CExt.rb_str_unlocktmp(@string)
      begin
        yield
      ensure
        Truffle::CExt.rb_str_locktmp(@string)
      end
    else
      yield
    end
  end
end
