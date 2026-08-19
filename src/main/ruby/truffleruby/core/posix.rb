# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle::POSIX
  Truffle::Boot.delay do
    # Used by IO#setup and post.rb
    NATIVE = Truffle::Boot.get_option 'platform-native'
  end

  EINTR = Errno::EINTR::Errno

  def self.setenv(name, value, overwrite)
    Primitive.posix_invalidate_env name
    setenv_native(name, value, overwrite)
  end

  def self.unsetenv(name)
    Primitive.posix_invalidate_env name
    unsetenv_native(name)
  end

  def self.with_array_of_ints(ints)
    if ints.empty?
      yield Truffle::FFI::Pointer::NULL
    else
      Truffle::FFI::MemoryPointer.new(:int, ints.size) do |ptr|
        ptr.write_array_of_int(ints)
        yield ptr
      end
    end
  end

  def self.with_array_of_strings_pointer(strings)
    Truffle::FFI::MemoryPointer.new(:pointer, strings.size + 1) do |ptr|
      buffer, *pointers = Truffle::FFI::Pool.stack_alloc(*strings.map { |s| s.bytesize + 1 })
      begin
        pointers.zip(strings) { |sp, s| Primitive.pointer_write_string(sp.address, s) }
        pointers << Truffle::FFI::Pointer::NULL
        ptr.write_array_of_pointer pointers
        yield(ptr)
      ensure
        Truffle::FFI::Pool.stack_free(buffer)
      end
    end
  end

  if Errno::EAGAIN::Errno == Errno::EWOULDBLOCK::Errno
    EAGAIN_ERRNO = Errno::EAGAIN::Errno
  else
    raise 'TruffleRuby currently assumes EAGAIN == EWOULDBLOCK'
  end

  # Used in IO#readpartial and IO::InternalBuffer#fill_read. Reads at least
  # one byte, blocking if it cannot read anything, but returning whatever it
  # gets as soon as it gets something.

  def self.read_string_at_least_one_byte(io, count)
    while true
      # must call #read_string in order to properly support polyglot STDIO
      string, errno = read_string(io, count)
      return string if errno == 0
      if errno == EAGAIN_ERRNO
        IO.select([io])
      else
        Errno.handle_errno(errno)
      end
    end
  end

  # Read up to count bytes of io to the thread-local IO buffer, and
  # yields the buffer (a FFI::Pointer) and bytes_read
  def self.read_to_buffer_at_least_one_byte(io, count, &block)
    while true
      # must call #read_to_buffer in order to properly support polyglot STDIO
      bytes_read, errno = read_to_buffer(io, count, &block)
      return bytes_read if errno == 0
      if errno == EAGAIN_ERRNO
        IO.select([io])
      else
        Errno.handle_errno(errno)
      end
    end
  end

  # Used in IO#read_nonblock

  def self.read_string_nonblock(io, count, exception)
    # must call #read_string in order to properly support polyglot STDIO.
    string, errno = read_string(io, count)
    if errno == 0
      string
    elsif errno == EAGAIN_ERRNO
      raise IO::EAGAINWaitReadable if exception
      :wait_readable
    else
      Errno.handle_errno(errno)
    end
  end

  # #read_string (either #read_string_native or #read_string_polyglot) is called
  # by IO#sysread

  def self.read_string_native(io, length)
    fd = io.fileno
    buffer = Primitive.io_thread_buffer_allocate(length)
    begin
      bytes_read = Truffle::POSIX.read(fd, buffer, length)
      if bytes_read < 0
        bytes_read, errno = bytes_read, Errno.errno
      elsif bytes_read == 0 # EOF
        bytes_read, errno = 0, 0
      else
        bytes_read, errno = bytes_read, 0
      end

      if bytes_read < 0
        [nil, errno]
      elsif bytes_read == 0 # EOF
        [nil, 0]
      else
        [buffer.read_string(bytes_read), 0]
      end
    ensure
      Primitive.io_thread_buffer_free(buffer)
    end
  end

  def self.read_to_buffer_native(io, length)
    fd = io.fileno
    buffer = Primitive.io_thread_buffer_allocate(length)
    begin
      bytes_read = Truffle::POSIX.read(fd, buffer, length)
      if bytes_read < 0
        bytes_read, errno = bytes_read, Errno.errno
      elsif bytes_read == 0 # EOF
        bytes_read, errno = 0, 0
      else
        bytes_read, errno = bytes_read, 0
      end

      if bytes_read < 0
        [-1, errno]
      elsif bytes_read == 0 # EOF
        [0, 0]
      else
        yield buffer, bytes_read
        [bytes_read, 0]
      end
    ensure
      Primitive.io_thread_buffer_free(buffer)
    end
  end

  def self.read_to_buffer_polyglot(io, length, &block)
    fd = io.fileno
    if fd == 0
      buffer = Primitive.io_thread_buffer_allocate(length)
      begin
        read = Primitive.io_read_polyglot length
        if read
          bytes_read = read.bytesize
          buffer.write_string_length(read, bytes_read)
          yield buffer, bytes_read
          [bytes_read, 0]
        else
          [0, 0]
        end
      ensure
        Primitive.io_thread_buffer_free(buffer)
      end
    else
      read_to_buffer_native(io, length, &block)
    end
  end

  def self.read_string_polyglot(io, length)
    fd = io.fileno
    if fd == 0
      read = Primitive.io_read_polyglot length
      [read, 0]
    else
      read_string_native(io, length)
    end
  end

  def self.pread_string(io, length, offset)
    fd = io.fileno
    buffer = Primitive.io_thread_buffer_allocate(length)

    begin
      bytes_read = Truffle::POSIX.pread(fd, buffer, length, offset)

      if bytes_read < 0 # error
        [nil, Errno.errno]
      elsif bytes_read == 0 # EOF
        [nil, 0]
      else
        [buffer.read_string(bytes_read), 0]
      end
    ensure
      Primitive.io_thread_buffer_free(buffer)
    end
  end

  # #write_string (either #write_string_native or #write_string_polyglot) is
  # called by IO#syswrite, IO#write, and IO::InternalBuffer#empty_to

  def self.write_string_native(io, string, continue_on_eagain)
    fd = io.fileno
    length = string.bytesize
    buffer = Primitive.io_thread_buffer_allocate(length)
    begin
      buffer.write_bytes string

      written = 0
      while written < length
        ret = Truffle::POSIX.write(fd, buffer + written, length - written)
        if ret < 0
          errno = Errno.errno
          if errno == EAGAIN_ERRNO
            if continue_on_eagain
              IO.select([], [io])
            else
              return written
            end
          else
            # stdout must raise a SIGPIPE SignalException instead of Errno::EPIPE
            # https://bugs.ruby-lang.org/issues/14413
            if fd == 1 and errno == Errno::EPIPE::Errno
              raise SignalException, :SIGPIPE
            end
            Errno.handle_errno(errno)
          end
        end
        written += ret
      end
      written
    ensure
      Primitive.io_thread_buffer_free(buffer)
    end
  end

  def self.write_string_polyglot(io, string, continue_on_eagain)
    fd = io.fileno
    if fd == 1 || fd == 2

      # continue_on_eagain is set for IO::InternalBuffer#empty_to, for IO#write
      # if @sync, but not for IO#syswrite. What happens in a polyglot stream
      # if we get EAGAIN and EWOULDBLOCK? We should try again if we do and
      # continue_on_eagain.

      Primitive.io_write_polyglot fd, string
    else
      write_string_native(io, string, continue_on_eagain)
    end
  end

  # #write_string_nonblock (either #write_string_nonblock_native or
  # #write_string_nonblock_polylgot) is called by IO#write_nonblock

  def self.write_string_nonblock_native(io, string)
    fd = io.fileno
    length = string.bytesize
    buffer = Primitive.io_thread_buffer_allocate(length)
    begin
      buffer.write_bytes string
      written = Truffle::POSIX.write(fd, buffer, length)

      if written < 0
        errno = Errno.errno
        if errno == EAGAIN_ERRNO
          raise IO::EAGAINWaitWritable
        else
          Errno.handle_errno(errno)
        end
      end
      written
    ensure
      Primitive.io_thread_buffer_free(buffer)
    end
  end

  def self.write_string_nonblock_polyglot(io, string)
    fd = io.fileno
    if fd == 1 || fd == 2

      # We only come here from IO#write_nonblock. What happens in a polyglot
      # stream if we get EAGAIN and EWOULDBLOCK? We should try again if we
      # we get them.

      Primitive.io_write_polyglot fd, string
    else
      write_string_nonblock_native(io, string)
    end
  end

  def self.pwrite_string(io, string, offset)
    fd = io.fileno
    length = string.bytesize
    buffer = Primitive.io_thread_buffer_allocate(length)

    begin
      buffer.write_bytes string

      written = Truffle::POSIX.pwrite(fd, buffer, length, offset)
      Errno.handle_errno(Errno.errno) if written < 0

      written
    ensure
      Primitive.io_thread_buffer_free(buffer)
    end
  end

  # Select between native and polyglot variants

  Truffle::Boot.delay do
    if Truffle::Boot.get_option('polyglot-stdio')
      class << self
        alias_method :read_string, :read_string_polyglot
        alias_method :read_to_buffer, :read_to_buffer_polyglot
        alias_method :write_string, :write_string_polyglot
        alias_method :write_string_nonblock, :write_string_nonblock_polyglot
      end
    else
      class << self
        alias_method :read_string, :read_string_native
        alias_method :read_to_buffer, :read_to_buffer_native
        alias_method :write_string, :write_string_native
        alias_method :write_string_nonblock, :write_string_nonblock_native
      end
    end
  end
end
