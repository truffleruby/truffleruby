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

  include Comparable

  Truffle::Boot.delay do
    PAGE_SIZE = Truffle::POSIX::NATIVE ? Truffle::POSIX.truffleposix_page_size : 4096

    if Truffle::POSIX::NATIVE and from_env = ENV['RUBY_IO_BUFFER_DEFAULT_SIZE']&.to_i and from_env > 0
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

  LITTLE_ENDIAN = 4
  BIG_ENDIAN = 8
  NETWORK_ENDIAN = BIG_ENDIAN
  HOST_ENDIAN = Primitive.little_endian? ? LITTLE_ENDIAN : BIG_ENDIAN

  SLICE = 512
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

  def self.size_of(types)
    if Primitive.is_a?(types, Array)
      return types.sum { size_of it }
    end
    type = types
    case type
    in :U8 | :S8
      1
    in :u16 | :U16 | :s16 | :S16
      2
    in :u32 | :U32 | :s32 | :S32 | :f32 | :F32
      4
    in :u64 | :U64 | :s64 | :S64 | :f64 | :F64
      8
    in :u128 | :U128 | :s128 | :S128
      16
    end
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
    address = pointer.address
    Errno.handle if address <= 0

    string = Truffle::CExt.rb_tr_static_native_string(address, size, Encoding::BINARY)
    ObjectSpace.define_finalizer(string, map_finalizer(address, size))

    new(string, flags)
  end

  private_class_method def self.map_finalizer(address, size)
    -> _id do
      Truffle::POSIX.munmap(address, size)
    end
  end

  def self.for(string)
    frozen = Primitive.frozen?(string)
    if block_given?
      flags = EXTERNAL | SLICE | (frozen ? READONLY : 0)
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
    raise ArgumentError, 'negative string size (or size too big)' if length < 0
    raise LocalJumpError, 'no block given' unless block_given?
    string = "\x00".b * length
    buffer = new(string, EXTERNAL)
    begin
      yield buffer
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

    # only set for slices, so we can write to the original String
    @parent = nil
    @offset = 0
  end

  def initialize_copy(original)
    initialize(original.size)
    copy(original)
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

  def <=>(other)
    (self.size <=> other.size).nonzero? || (get_string <=> other.get_string)
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
    # Not freeing anything eagerly here, the @string will free the associated resources once it's GC'ed
    @string = NULL
    @flags = 0
    self
  end

  def slice(offset = 0, length = self.size - offset)
    validate_offset_and_length(offset, length)

    string_slice = null? ? NULL : Primitive.string_byteslice_without_copying(@string, offset, length)
    slice = IO::Buffer.new(string_slice, SLICE | (readonly? ? READONLY : 0))

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

      if !Truffle::Platform.linux? && mapped?
        @flags = ((@flags & ~MAPPED) | INTERNAL)
      end
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

    copy = Primitive.class(self).allocate
    copy.send(:initialize, @string, @flags)
    @string = NULL
    copy
  end

  def each_byte(offset = 0, count = size, &block)
    @string.byteslice(offset, count).each_byte(&block)
  end

  def each(type, offset = 0, count = size)
    return to_enum(:each, type, offset, count) unless block_given?

    stride = IO::Buffer.size_of(type)
    to = [offset + count * stride, size].min
    while offset < to
      yield offset, get_value(type, offset)
      offset += stride
    end
    self
  end

  def values(type, offset = 0, count = size)
    each(type, offset, count).map { |_offset, value| value }
  end

  def get_value(type, offset)
    to = offset + IO::Buffer.size_of(type)
    if to > size
      raise ArgumentError, "Type extends beyond end of buffer! (offset=#{offset} > size=#{size})"
    end

    case type
    in :U8 then @string.getbyte(offset)
    in :S8 then @string.unpack1('c', offset:)
    in :U16 then @string.unpack1('S>', offset:)
    in :S16 then @string.unpack1('s>', offset:)
    in :u16 then @string.unpack1('S<', offset:)
    in :s16 then @string.unpack1('s<', offset:)
    in :U32 then @string.unpack1('L>', offset:)
    in :S32 then @string.unpack1('l>', offset:)
    in :u32 then @string.unpack1('L<', offset:)
    in :s32 then @string.unpack1('l<', offset:)
    in :U64 then @string.unpack1('Q>', offset:)
    in :S64 then @string.unpack1('q>', offset:)
    in :u64 then @string.unpack1('Q<', offset:)
    in :s64 then @string.unpack1('q<', offset:)
    in :U128 then (@string.unpack1('Q>', offset:) << 64) + @string.unpack1('Q>', offset: offset + 8)
    in :S128 then (@string.unpack1('q>', offset:) << 64) + @string.unpack1('Q>', offset: offset + 8)
    in :u128 then @string.unpack1('Q<', offset:) + (@string.unpack1('Q<', offset: offset + 8) << 64)
    in :s128 then @string.unpack1('Q<', offset:) + (@string.unpack1('q<', offset: offset + 8) << 64)
    in :F32 then @string.unpack1('g', offset:)
    in :f32 then @string.unpack1('e', offset:)
    in :F64 then @string.unpack1('G', offset:)
    in :f64 then @string.unpack1('E', offset:)
    end
  end

  def set_value(type, offset, value)
    check_writable
    to = offset + IO::Buffer.size_of(type)
    if to > size
      raise ArgumentError, "Type extends beyond end of buffer! (offset=#{offset} > size=#{size})"
    end

    case type
    in :U8 | :S8 then write_byte(offset, value)
    in :U16 | :S16 then write_string(offset, [value].pack('S>'))
    in :u16 | :s16 then write_string(offset, [value].pack('S<'))
    in :U32 | :S32 then write_string(offset, [value].pack('L>'))
    in :u32 | :s32 then write_string(offset, [value].pack('L<'))
    in :U64 | :S64 then write_string(offset, [value].pack('Q>'))
    in :u64 | :s64 then write_string(offset, [value].pack('Q<'))
    in :U128 | :S128
      high, low = value.divmod(1 << 64)
      write_string(offset, [high].pack('Q>'))
      write_string(offset + 8, [low].pack('Q>'))
    in :u128 | :s128
      high, low = value.divmod(1 << 64)
      write_string(offset, [low].pack('Q<'))
      write_string(offset + 8, [high].pack('Q<'))
    in :F32 then write_string(offset, [value].pack('g'))
    in :f32 then write_string(offset, [value].pack('e'))
    in :F64 then write_string(offset, [value].pack('G'))
    in :f64 then write_string(offset, [value].pack('E'))
    end

    to
  end

  def get_values(types, offset)
    types.map do |type|
      value = get_value(type, offset)
      offset += IO::Buffer.size_of(type)
      value
    end
  end

  def set_values(types, offset, values)
    types.zip(values) do |type, value|
      set_value(type, offset, value)
      offset += IO::Buffer.size_of(type)
    end
    offset
  end

  def clear(value = 0, offset = 0, length = self.size - offset)
    validate_offset_and_length(offset, length)
    (offset...offset + length).each do |byte_offset|
      write_byte(byte_offset, value)
    end
    self
  end

  def get_string(offset = 0, length = self.size - offset, encoding = Encoding::BINARY)
    validate_offset_and_length(offset, length)

    @string.byteslice(offset, length).force_encoding(encoding)
  end

  def set_string(string, offset = 0, source_length = string.bytesize, source_offset = 0)
    check_writable
    if source_offset != 0 || source_length != string.bytesize
      string = string.byteslice(source_offset, source_length)
    end

    write_string(offset, string)
    self
  end

  def copy(source, offset = 0, source_length = source.size, source_offset = 0)
    check_writable
    string = source.get_string(source_offset, source_length)
    write_string(offset, string)
    self
  end

  def read(io, length = nil, offset = 0)
    length = self.size - offset if Primitive.nil?(length) || length == 0
    validate_offset_and_length(offset, length)

    string = io.read(length)
    write_string(offset, string)
    string.bytesize
  end

  def pread(io, from, length = nil, offset = 0)
    length = self.size - offset if Primitive.nil?(length) || length == 0
    validate_offset_and_length(offset, length)

    string = io.pread(length, from)
    write_string(offset, string)
    string.bytesize
  end

  def write(io, length = nil, offset = 0)
    length = self.size - offset if Primitive.nil?(length) || length == 0
    string = get_string(offset, length)
    io.write string
  end

  def pwrite(io, from, length = nil, offset = 0)
    length = self.size - offset if Primitive.nil?(length) || length == 0
    string = get_string(offset, length)
    io.pwrite string, from
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
    self_size = self.size
    i = 0
    while i < self_size
      write_byte(i, ~@string.getbyte(i))
      i += 1
    end
    self
  end

  def and!(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    self_size = self.size
    mask_size = mask.size
    i = 0
    while i < self_size
      write_byte(i, @string.getbyte(i) & mask.get_value(:U8, i % mask_size))
      i += 1
    end
    self
  end

  def or!(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    self_size = self.size
    mask_size = mask.size
    i = 0
    while i < self_size
      write_byte(i, @string.getbyte(i) | mask.get_value(:U8, i % mask_size))
      i += 1
    end
    self
  end

  def xor!(mask)
    Truffle::Type.rb_check_type(mask, IO::Buffer)
    self_size = self.size
    mask_size = mask.size
    i = 0
    while i < self_size
      write_byte(i, @string.getbyte(i) ^ mask.get_value(:U8, i % mask_size))
      i += 1
    end
    self
  end

  private def storage
    if @parent
      Primitive.object_ivar_get(@parent, :@string)
    else
      @string
    end
  end

  private def write_byte(offset, byte)
    Primitive.string_setbyte(storage, offset + @offset, byte)
  end

  private def write_string(offset, string)
    return if string.empty?
    storage = self.storage
    Primitive.check_frozen(storage)

    enc = storage.encoding
    compat_enc = Primitive.encoding_ensure_compatible_str(storage, string)
    unless compat_enc == enc
      string = string.dup.force_encoding(enc)
    end

    Primitive.string_splice(storage, string, offset + @offset, string.bytesize, enc)
  end

  private def validate_offset_and_length(offset, length)
    raise InvalidatedError, 'Buffer has been invalidated!' unless valid?
    raise ArgumentError, "Offset can't be negative!" if offset < 0
    raise ArgumentError, 'The given offset is bigger than the buffer size!' if offset > self.size
    raise ArgumentError, 'Specified offset+length is bigger than the buffer size!' if offset + length > self.size
  end

  private def check_writable
    raise InvalidatedError, 'Buffer has been invalidated!' unless valid?
    raise AccessError, 'Buffer is not writable!' if readonly? || (Primitive.frozen?(@string) && !null?)
  end
end
