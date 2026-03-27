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

  class LazyLibrary
    def initialize(&block)
      @block = block
      @resolved = nil
    end

    def resolve
      if resolved = @resolved
        resolved
      else
        raise 'loading library while pre-initializing' if Truffle::Boot.preinitializing?
        raise SecurityError, 'native access is not allowed' unless NATIVE

        TruffleRuby.synchronized(self) do
          @resolved ||= @block.call
        end
      end
    end
  end

  LIBC = LazyLibrary.new do
    Primitive.interop_eval_nfi 'default'
  end

  LIBTRUFFLEPOSIX = LazyLibrary.new do
    libtruffleposix = "#{Truffle::Boot.ruby_home}/lib/cext/libtruffleposix.#{Truffle::Platform::SOEXT}"
    Primitive.interop_eval_nfi "load '#{libtruffleposix}'"
  end

  LIBCRYPT = LazyLibrary.new do
    if Truffle::Platform.linux?
      Primitive.interop_eval_nfi 'load libcrypt.so'
    else
      LIBC.resolve
    end
  end

  TYPES = {
    :short => :sint16,
    :ushort => :uint16,
    :int => :sint32,
    :uint => :uint32,
    :long => :sint64,
    :ulong => :uint64,
    :long_long => :sint64,
    :ulong_long => :uint64,
  }

  EINTR = Errno::EINTR::Errno

  def self.to_nfi_type(type)
    TruffleRuby.synchronized(TYPES) do
      if found = TYPES[type]
        found
      elsif typedef = Truffle::Config.lookup("platform.typedef.#{type}")
        TYPES[type] = to_nfi_type(typedef.to_sym)
      else
        TYPES[type] = type
      end
    end
  end

  def self.varargs(type)
    :"...#{to_nfi_type(type)}"
  end

  # the actual function is looked up and attached on its first call
  def self.attach_function(native_name, argument_types, return_type,
                           library = LIBC, blocking = false, method_name = native_name, on = self)

    on.define_singleton_method method_name, -> *args do
      Truffle::POSIX.attach_function_eagerly native_name, argument_types, return_type,
                                             library, blocking, method_name, on
      __send__ method_name, *args
    end
  end

  def self.attach_function_eagerly(native_name, argument_types, return_type,
                                   library, blocking, method_name, on)

    library = library.resolve
    begin
      func = library[native_name]
    rescue NameError # Missing function
      func = nil
    end

    if func
      string_args = []
      nfi_args_types = []
      argument_types.each_with_index do |arg_type, i|
        if arg_type == :string
          string_args << i
          nfi_args_types << '[sint8]'
        else
          nfi_args_types << to_nfi_type(arg_type)
        end
      end
      string_args.freeze

      nfi_return_type = to_nfi_type(return_type)
      if nfi_return_type.to_s.start_with?('uint')
        unsigned_return_type = 1 << nfi_return_type.to_s[-2..-1].to_i
      end

      parsed_sig = Primitive.interop_eval_nfi "(#{nfi_args_types.join(',')}):#{nfi_return_type}"
      bound_func = parsed_sig.bind(func)

      method_body = Truffle::Graal.copy_captured_locals -> *args do
        string_args.each do |i|
          str = args.fetch(i)
          # TODO CS 14-Nov-17 this involves copying to a Java byte[], and then NFI will copy it again!
          args[i] = Primitive.string_to_null_terminated_byte_array str
        end

        if blocking
          begin
            result = Primitive.thread_run_blocking_nfi_system_call(bound_func, args)
          end while Primitive.is_a?(result, Integer) and result == -1 and Errno.errno == EINTR
        else
          result = Primitive.interop_execute(bound_func, args)
        end

        if return_type == :string
          if Primitive.interop_null?(result)
            result = nil
          else
            ptr = Truffle::FFI::Pointer.new(Truffle::Interop.as_pointer(result))
            result = ptr.read_string_to_null
          end
        elsif return_type == :pointer
          result = Truffle::FFI::Pointer.new(Truffle::Interop.as_pointer(result))
        elsif return_type == :ssize_t
          result = Primitive.integer_lower(result)
        elsif unsigned_return_type
          if result >= 0
            result
          else
            result += unsigned_return_type
          end
        end

        result
      end
      on.define_singleton_method method_name, method_body
    else
      on.define_singleton_method method_name, -> * do
        raise NotImplementedError, "#{native_name} is not available"
      end
      Primitive.method_unimplement method(method_name)
    end
  end

  # Filesystem-related
  attach_function :chdir, [:string], :int
  attach_function :chmod, [:string, :mode_t], :int
  attach_function :chown, [:string, :uid_t, :gid_t], :int
  attach_function :chroot, [:string], :int
  attach_function :truffleposix_clock_getres, [:int], :int64_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_clock_gettime, [:int], :int64_t, LIBTRUFFLEPOSIX
  attach_function :close, [:int], :int
  attach_function :closedir, [:pointer], :int
  attach_function :dirfd, [:pointer], :int
  attach_function :dup, [:int], :int
  attach_function :dup2, [:int, :int], :int
  attach_function :fchdir, [:int], :int
  attach_function :fchmod, [:int, :mode_t], :int
  attach_function :fchown, [:int, :uid_t, :gid_t], :int
  attach_function :fcntl, [:int, :int, varargs(:int)], :int
  attach_function :fdopendir, [:int], :pointer
  attach_function :flock, [:int, :int], :int, LIBC, true
  attach_function :truffleposix_fstat, [:int, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_fstat_mode, [:int], :mode_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_fstat_size, [:int], :long, LIBTRUFFLEPOSIX
  attach_function :truffleposix_fstatat, [:int, :string, :pointer, :int], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_fstatat_mode, [:int, :string, :int], :mode_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_fstatat_size, [:int, :string, :int], :long, LIBTRUFFLEPOSIX
  attach_function :fsync, [:int], :int
  attach_function :ftruncate, [:int, :off_t], :int
  attach_function :getcwd, [:pointer, :size_t], :string
  attach_function :ioctl, [:int, :ulong, varargs(:pointer)], :int
  attach_function :isatty, [:int], :int
  attach_function :lchmod, [:string, :mode_t], :int
  attach_function :lchown, [:string, :uid_t, :gid_t], :int
  attach_function :link, [:string, :string], :int
  attach_function :lseek, [:int, :off_t, :int], :off_t
  attach_function :truffleposix_lstat, [:string, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_lstat_mode, [:string], :mode_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_lutimes, [:string, :long, :int, :long, :int], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_major, [:dev_t], :uint, LIBTRUFFLEPOSIX
  attach_function :truffleposix_minor, [:dev_t], :uint, LIBTRUFFLEPOSIX
  attach_function :mkdir, [:string, :mode_t], :int
  attach_function :mkfifo, [:string, :mode_t], :int
  attach_function :open, [:string, :int, varargs(:mode_t)], :int
  attach_function :opendir, [:string], :pointer
  attach_function :pipe, [:pointer], :int
  # blocking=false for both poll because the timeout needs to be decreased on EINTR
  attach_function :truffleposix_poll_single_fd, [:int, :int, :int], :int, LIBTRUFFLEPOSIX
  attach_function :poll, [:pointer, :nfds_t, :int], :int
  attach_function :read, [:int, :pointer, :size_t], :ssize_t, LIBC, true
  attach_function :pread, [:int, :pointer, :size_t, :off_t], :ssize_t, LIBC, true
  attach_function :readlink, [:string, :pointer, :size_t], :ssize_t
  attach_function :realpath, [:string, :pointer], :pointer
  attach_function :truffleposix_readdir_multiple, [:pointer, :int, :int, :int, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_readdir_name, [:pointer], :string, LIBTRUFFLEPOSIX
  attach_function :rename, [:string, :string], :int
  attach_function :truffleposix_rewinddir, [:pointer], :void, LIBTRUFFLEPOSIX
  attach_function :rmdir, [:string], :int
  attach_function :seekdir, [:pointer, :long], :void
  attach_function :truffleposix_stat, [:string, :pointer], :int, LIBTRUFFLEPOSIX
  attach_function :truffleposix_stat_mode, [:string], :mode_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_stat_size, [:string], :long, LIBTRUFFLEPOSIX
  attach_function :symlink, [:string, :string], :int
  attach_function :telldir, [:pointer], :long
  attach_function :truncate, [:string, :off_t], :int
  attach_function :umask, [:mode_t], :mode_t
  attach_function :unlink, [:string], :int
  attach_function :truffleposix_utimes, [:string, :long, :int, :long, :int], :int, LIBTRUFFLEPOSIX
  attach_function :write, [:int, :pointer, :size_t], :ssize_t, LIBC, true
  attach_function :pwrite, [:int, :pointer, :size_t, :off_t], :ssize_t, LIBC, true

  Truffle::Boot.delay do
    if NATIVE
      # We should capture the non-lazy method
      attach_function_eagerly :poll, [:pointer, :nfds_t, :int], :int, LIBC, false, :poll, self
      POLL = method(:poll)

      attach_function_eagerly :truffleposix_poll_single_fd, [:int, :int, :int], :int, LIBTRUFFLEPOSIX, false, :truffleposix_poll_single_fd, self
      POLL_SINGLE_FD = method(:truffleposix_poll_single_fd)
    end
  end

  # Process-related
  attach_function :getegid, [], :gid_t
  attach_function :getgid, [], :gid_t
  attach_function :setresgid, [:gid_t, :gid_t, :gid_t], :int
  attach_function :setregid, [:gid_t, :gid_t], :int
  attach_function :setegid, [:uid_t], :int
  attach_function :setgid, [:gid_t], :int

  attach_function :geteuid, [], :uid_t
  attach_function :getuid, [], :uid_t
  attach_function :setresuid, [:uid_t, :uid_t, :uid_t], :int
  attach_function :setreuid, [:uid_t, :uid_t], :int
  attach_function :setruid, [:uid_t], :int
  attach_function :seteuid, [:uid_t], :int
  attach_function :setuid, [:uid_t], :int

  attach_function :getpid, [], :pid_t
  attach_function :getppid, [], :pid_t
  attach_function :kill, [:pid_t, :int], :int
  attach_function :getpgrp, [], :pid_t
  attach_function :getpgid, [:pid_t], :pid_t
  attach_function :setpgid, [:pid_t, :pid_t], :int
  attach_function :setsid, [], :pid_t

  attach_function :getgroups, [:int, :pointer], :int
  attach_function :setgroups, [:size_t, :pointer], :int

  attach_function :getrlimit, [:int, :pointer], :int
  attach_function :setrlimit, [:int, :pointer], :int
  attach_function :truffleposix_getrusage, [:pointer], :int, LIBTRUFFLEPOSIX

  attach_function :truffleposix_getpriority, [:int, :id_t], :int, LIBTRUFFLEPOSIX
  attach_function :setpriority, [:int, :id_t, :int], :int

  attach_function :execve, [:string, :pointer, :pointer], :int
  attach_function :truffleposix_posix_spawnp, [:string, :pointer, :pointer, :int, :pointer, :int, :int, :pointer], :pid_t, LIBTRUFFLEPOSIX
  attach_function :truffleposix_waitpid, [:pid_t, :int, :pointer], :pid_t, LIBTRUFFLEPOSIX, true

  # ENV-related
  attach_function :getenv, [:string], :string

  attach_function :setenv, [:string, :string, :int], :int, LIBC, false, :setenv_native
  def self.setenv(name, value, overwrite)
    Primitive.posix_invalidate_env name
    setenv_native(name, value, overwrite)
  end

  attach_function :unsetenv, [:string], :int, LIBC, false, :unsetenv_native
  def self.unsetenv(name)
    Primitive.posix_invalidate_env name
    unsetenv_native(name)
  end

  # Other routines
  attach_function :crypt, [:string, :string], :string, LIBCRYPT
  attach_function :truffleposix_get_current_user_home, [], :pointer, LIBTRUFFLEPOSIX
  attach_function :truffleposix_get_user_home, [:string], :pointer, LIBTRUFFLEPOSIX
  attach_function :truffleposix_free, [:pointer], :void, LIBTRUFFLEPOSIX

  # Errno-related
  if Truffle::Platform.linux?
    attach_function :__errno_location, [], :pointer, LIBC, false, :errno_address
  elsif Truffle::Platform.darwin?
    attach_function :__error, [], :pointer, LIBC, false, :errno_address
  else
    raise 'Unsupported platform'
  end

  # Platform-specific
  unless Truffle::Platform.darwin?
    attach_function :dup3, [:int, :int, :int], :int
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
        pointers.zip(strings) { |sp, s| sp.put_string(0, s) }
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
