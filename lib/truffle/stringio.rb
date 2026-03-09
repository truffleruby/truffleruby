# frozen_string_literal: false
# truffleruby_primitives: true

# Copyright (c) 2013, Brian Shirai
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 3. Neither the name of the library nor the names of its contributors may be
#    used to endorse or promote products derived from this software without
#    specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
# OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
# EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
require 'truffle/cext'

Truffle::CExt.rb_define_module_under(IO, 'generic_readable').module_eval do
  # This is why we need undefined in Ruby
  Undefined = Object.new

  def readchar
    raise EOFError, 'end of file reached' if eof?
    getc
  end

  def readbyte
    readchar.getbyte(0)
  end

  def readline(sep = $/, limit = ::Undefined, chomp: false)
    check_readable
    raise EOFError, 'end of file reached' if eof?

    Primitive.io_last_line_set(Primitive.caller_special_variables, getline(false, sep, limit, chomp))
  end

  def sysread(length = nil, buffer = nil)
    str = read(length, buffer)

    if Primitive.nil?(str)
      raise EOFError, 'end of file reached'
    end

    str
  end
  alias_method :readpartial, :sysread

  def read_nonblock(length, buffer = nil, exception: true)
    str = read(length, buffer)

    if exception and Primitive.nil?(str)
      raise EOFError, 'end of file reached'
    end

    str
  end

end

Truffle::CExt.rb_define_module_under(IO, 'generic_writable').module_eval do
  def <<(str)
    write(str)
    self
  end

  def print(*args)
    check_writable
    args << Primitive.io_last_line_get(Primitive.caller_special_variables) if args.empty?
    write((args << $\).flatten.join)
    nil
  end

  def printf(*args)
    check_writable

    write(sprintf(*args))

    nil
  end

  def puts(*args)
    Truffle::IOOperations.puts(self, *args)
  end

  def write_nonblock(str, exception: true)
    write(str)
  end

  def syswrite(str)
    write(str)
  end
end

class StringIO

  VERSION = '3.0.1' # Same version as the default gem in CRuby 3.1.3

  include Enumerable
  include Truffle::CExt.rb_define_module_under(IO, 'generic_readable')
  include Truffle::CExt.rb_define_module_under(IO, 'generic_writable')

  DEFAULT_RECORD_SEPARATOR = "\n" unless defined?(::DEFAULT_RECORD_SEPARATOR)

  class Data
    attr_accessor :string, :pos, :lineno, :encoding

    def initialize(string)
      @string = string
      @encoding = string.encoding
      @pos = @lineno = 0
    end
  end

  def self.open(*args)
    io = new(*args)
    return io unless block_given?

    begin
      yield io
    ensure
      io.close
      io.__data__.string = nil
    end
  end

  attr_reader :__data__

  def initialize(string = nil, mode = nil, **options)
    if block_given?
      warn 'StringIO::new() does not take block; use StringIO::open() instead'
    end

    mode, _binary, _external, _internal, _autoclose, _perm = Truffle::IOOperations.normalize_options(mode, nil, options)

    if Primitive.nil?(string)
      mode ||= IO::RDWR
      @__data__ = Data.new ''.force_encoding(Encoding.default_external)
    else
      string = Primitive.convert_with_to_str string
      @__data__ = Data.new string
    end

    if mode
      if Primitive.is_a?(mode, Integer)
        mode_from_integer(mode)
      else
        mode = Primitive.convert_with_to_str(mode)
        mode_from_string(mode)
      end
    else
      mode_from_string(string.frozen? ? 'r' : 'r+')
    end

    if @writable && @__data__.string.frozen?
      raise Errno::EACCES, 'Permission denied'
    end

    if @truncate
      @__data__.string.replace(''.force_encoding(@__data__.string.encoding))
    end

    self
  end

  def initialize_copy(from)
    from = Primitive.convert_type(from, StringIO, :to_strio)

    @append = from.instance_variable_get(:@append)
    @readable = from.instance_variable_get(:@readable)
    @writable = from.instance_variable_get(:@writable)
    @__data__ = from.instance_variable_get(:@__data__)

    self
  end

  # StringIO#inspect should not include the contents of the StringIO
  alias_method :inspect, :to_s

  private def check_readable
    raise IOError, 'not opened for reading' unless @readable
  end

  private def check_writable
    raise IOError, 'not opened for writing' unless @writable
    raise IOError, 'unable to modify data' if @__data__.string.frozen?
  end

  def set_encoding(external, internal = nil, options = nil)
    encoding = Truffle::Type.coerce_to_encoding(external || Encoding.default_external)
    d = @__data__
    TruffleRuby.synchronized(d) do
      d.encoding = encoding
      d.string.force_encoding(encoding) if @writable
    end

    self
  end

  def set_encoding_by_bom
    Primitive.check_frozen self
    return nil unless @readable

    encoding = Truffle::IOOperations.strip_bom(self)
    return nil unless encoding

    d = @__data__
    TruffleRuby.synchronized(d) do
      d.encoding = encoding
      d.string.force_encoding(encoding) if @writable
    end

    encoding
  end

  def external_encoding
    @__data__.encoding
  end

  def internal_encoding
    nil
  end

  def each_byte
    return to_enum :each_byte unless block_given?
    check_readable

    d = @__data__
    string = d.string

    while d.pos < string.bytesize
      check_readable
      byte = string.getbyte d.pos
      d.pos += 1
      yield byte
    end

    self
  end

  def each_char
    return to_enum :each_char unless block_given?
    while s = getc
      yield s
    end

    self
  end

  def each_codepoint(&block)
    return to_enum :each_codepoint unless block_given?
    check_readable

    d = @__data__
    string = d.string

    while d.pos < string.bytesize
      check_readable
      char = Primitive.string_chr_at(string, d.pos)

      unless char
        raise ArgumentError, "invalid byte sequence in #{d.encoding}"
      end

      d.pos += char.bytesize
      yield char.ord
    end

    self
  end

  def each(sep = $/, limit = Undefined, chomp: false)
    return to_enum :each, sep, limit, chomp: chomp unless block_given?
    check_readable

    while line = getline(true, sep, limit, chomp)
      yield line
    end

    self
  end
  alias_method :each_line, :each

  def binmode
    set_encoding(Encoding::BINARY)
    self
  end

  def write(str)
    check_writable

    str = String(str)
    return 0 if str.empty?

    # difference to IO, see https://github.com/ruby/stringio/blob/009896b973/ext/stringio/stringio.c#L1498-L1506
    enc = external_encoding
    unless enc == Encoding::BINARY or enc == Encoding::US_ASCII
      unless !str.ascii_only? && (str.encoding == Encoding::BINARY || str.encoding == Encoding::US_ASCII)
        str = Truffle::IOOperations.write_transcoding(str, enc)
      end
    end

    d = @__data__
    TruffleRuby.synchronized(d) do
      pos = d.pos
      string = d.string
      bytesize = string.bytesize

      if @append || pos == bytesize
        Primitive.string_byte_append(string, str)
        d.pos = string.bytesize
      elsif pos > bytesize
        replacement = "\000" * (pos - bytesize)
        Primitive.string_byte_append(string, replacement)
        Primitive.string_byte_append(string, str)
        d.pos = string.bytesize
      else
        bytes_to_replace = str.bytesize
        bytes_after = bytesize - pos
        if bytes_to_replace > bytes_after
          bytes_to_replace = bytes_after
        end

        enc = string.encoding
        str_in_enc = str.dup.force_encoding(enc)
        Primitive.string_splice(string, str_in_enc, pos, bytes_to_replace, enc)
        d.pos += str.bytesize
      end

      str.bytesize
    end
  end

  def close
    @readable = @writable = nil
  end

  def closed?
    !@readable && !@writable
  end

  def close_read
    check_readable
    @readable = nil
  end

  def closed_read?
    !@readable
  end

  def close_write
    check_writable
    @writable = nil
  end

  def closed_write?
    !@writable
  end

  def eof?
    d = @__data__
    TruffleRuby.synchronized(d) do
      d.pos >= d.string.bytesize
    end
  end
  alias_method :eof, :eof?

  def fcntl
    raise NotImplementedError, 'StringIO#fcntl is not implemented'
  end

  def fileno
    nil
  end

  def flush
    self
  end

  def fsync
    0
  end

  def getc
    check_readable
    return nil if eof?

    d = @__data__
    TruffleRuby.synchronized(d) do
      char = Primitive.string_find_character(d.string, d.pos)
      d.pos += char.bytesize
      char
    end
  end

  def getbyte
    check_readable
    return nil if eof?

    d = @__data__
    TruffleRuby.synchronized(d) do
      byte = d.string.getbyte(d.pos)
      d.pos += 1
      byte
    end
  end

  def gets(sep = $/, limit = Undefined, chomp: false)
    check_readable

    # Truffle: $_ is thread and frame local, so we use a primitive to
    # set it in the caller's frame.
    Primitive.io_last_line_set(Primitive.caller_special_variables, getline(false, sep, limit, chomp))
  end

  def isatty
    false
  end
  alias_method :tty?, :isatty

  def lineno
    @__data__.lineno
  end

  def lineno=(line)
    @__data__.lineno = line
  end

  def pid
    nil
  end

  def pos
    @__data__.pos
  end

  def pos=(pos)
    raise Errno::EINVAL if pos < 0
    @__data__.pos = pos
  end

  def putc(obj)
    check_writable

    if Primitive.is_a?(obj, String)
      char = obj[0]
    else
      c = Primitive.convert_with_to_int obj
      char = (c & 0xff).chr
    end

    write(char)
    obj
  end

  def read(length = nil, buffer = nil)
    check_readable
    d = @__data__
    TruffleRuby.synchronized(d) do
      pos = d.pos
      string = d.string

      # intentionally don't preserve buffer's encoding
      # see https://bugs.ruby-lang.org/issues/20418
      if length
        length = Primitive.convert_with_to_int length
        raise ArgumentError if length < 0

        buffer = Primitive.convert_with_to_str(buffer) if buffer

        if eof?
          buffer.clear if buffer
          if length == 0
            return ''.force_encoding(Encoding::ASCII_8BIT)
          else
            return nil
          end
        end

        str = string.byteslice(pos, length)
        str.force_encoding Encoding::ASCII_8BIT

        str = buffer.replace(str) if buffer
      else
        if eof?
          buffer.clear if buffer
          return ''.force_encoding(Encoding::ASCII_8BIT)
        end

        str = string.byteslice(pos..-1)
        buffer.replace str if buffer
      end

      d.pos += str.bytesize
      str
    end
  end

  def readlines(sep = $/, limit = Undefined, chomp: false)
    check_readable

    ary = []
    while line = getline(true, sep, limit, chomp)
      ary << line
    end

    ary
  end

  def reopen(string = nil, mode = Undefined)
    if string and not Primitive.is_a?(string, String) and Primitive.equal?(mode, Undefined)
      stringio = Primitive.convert_type(string, StringIO, :to_strio)

      initialize_copy stringio
    else
      mode = nil if Primitive.equal?(mode, Undefined)
      string = '' unless string

      initialize string, mode
    end

    self
  end

  def rewind
    d = @__data__
    TruffleRuby.synchronized(d) do
      d.pos = d.lineno = 0
    end
  end

  def seek(to, whence = IO::SEEK_SET)
    raise IOError, 'closed stream' if self.closed?
    to = Primitive.convert_with_to_int to

    d = @__data__
    TruffleRuby.synchronized(d) do
      case whence
      when IO::SEEK_CUR
        to += d.pos
      when IO::SEEK_END
        to += d.string.bytesize
      when IO::SEEK_SET, nil
      else
        raise Errno::EINVAL, 'invalid whence'
      end

      raise Errno::EINVAL if to < 0

      d.pos = to
    end

    0
  end

  def size
    @__data__.string.bytesize
  end
  alias_method :length, :size

  def string
    @__data__.string
  end

  def string=(string)
    d = @__data__
    TruffleRuby.synchronized(d) do
      d.string = Primitive.convert_with_to_str(string)
      d.pos = 0
      d.lineno = 0
    end
  end

  def sync
    true
  end

  def sync=(val)
    val
  end

  def tell
    @__data__.pos
  end

  def truncate(length)
    check_writable
    len = Primitive.convert_with_to_int length
    raise Errno::EINVAL, 'negative length' if len < 0

    d = @__data__
    TruffleRuby.synchronized(d) do
      string = d.string
      bytesize = string.bytesize

      if len < bytesize
        string[len..bytesize] = ''
      else
        string << "\000" * (len - bytesize)
      end
    end

    length
  end

  def ungetc(char)
    check_readable

    if Primitive.is_a?(char, Integer)
      char = Primitive.convert_type char, String, :chr
    else
      char = Primitive.convert_with_to_str char
    end

    d = @__data__
    TruffleRuby.synchronized(d) do
      pos = d.pos
      string = d.string
      bytesize = string.bytesize

      if pos > bytesize
        string[bytesize..pos] = "\000" * (pos - bytesize)
        d.pos -= 1
        string[d.pos] = char
      elsif pos > 0
        d.pos -= 1
        string[d.pos] = char
      end
    end

    nil
  end

  def ungetbyte(bytes)
    check_readable

    return unless bytes

    if Primitive.is_a?(bytes, Integer)
      bytes = ''.b << (bytes & 0xff)
    else
      bytes = Primitive.convert_with_to_str(bytes).b
      return if bytes.bytesize == 0
    end

    d = @__data__
    TruffleRuby.synchronized(d) do
      pos = d.pos
      string = d.string.b

      enc = d.string.encoding

      if d.pos == 0
        d.string = bytes << string
      else
        size = bytes.bytesize
        a = string.byteslice(0, pos - size) if size < pos
        b = string.byteslice(pos..-1)
        d.string = "#{a}#{bytes}#{b}"
        d.pos = pos > size ? pos - size : 0
      end

      d.string.force_encoding enc
    end

    nil
  end

  def encode_with(coder)
  end

  def init_with(coder)
    @__data__ = Data.new('')
  end

  def to_yaml_properties
    []
  end

  def yaml_initialize(type, val)
    @__data__ = Data.new('')
  end

  private def mode_from_string(mode)
    @append = @truncate = false

    if mode[0] == ?r
      @readable = true
      @writable = mode[-1] == ?+ ? true : false
    end

    if mode[0] == ?w
      @writable = @truncate = true
      @readable = mode[-1] == ?+ ? true : false
    end

    if mode[0] == ?a
      @append = @writable = true
      @readable = mode[-1] == ?+ ? true : false
    end
  end

  private def mode_from_integer(mode)
    @readable = @writable = @append = @truncate = false

    if mode == 0 or mode & IO::RDWR != 0
      @readable = true
    end

    if mode & (IO::WRONLY | IO::RDWR) != 0
      @writable = true
    end

    @append = true if (mode & IO::APPEND) != 0
    @truncate = true if (mode & IO::TRUNC) != 0
  end

  private def getline(arg_error, sep, limit, chomp = false)
    if limit != Undefined
      limit = Primitive.convert_with_to_int limit if limit
      sep = Primitive.convert_with_to_str sep if sep
    else
      limit = nil

      unless sep == $/ or Primitive.nil?(sep)
        osep = sep
        sep = Truffle::Type.rb_check_convert_type sep, String, :to_str
        unless sep
          limit = Primitive.convert_with_to_int osep
          sep = $/
        end
      end
    end

    raise ArgumentError if arg_error and limit == 0

    limit = nil if limit && limit < 0

    return nil if eof?

    line = nil
    d = @__data__
    TruffleRuby.synchronized(d) do
      pos = d.pos
      string = d.string
      bytesize = string.bytesize

      if Primitive.nil?(sep)
        if limit
          line = string.byteslice(pos, limit)
        else
          line = string.byteslice(pos, bytesize - pos)
        end
        d.pos += line.bytesize
      elsif sep.empty?
        if stop = Primitive.find_string(string, "\n\n", pos)
          stop += 2
          line = string.byteslice(pos, stop - pos)
          while string.getbyte(stop) == 10
            stop += 1
          end
          d.pos = stop
        else
          line = string.byteslice(pos, bytesize - pos)
          d.pos = bytesize
        end
      else
        if stop = Primitive.find_string(string, sep, pos)
          if limit && stop - pos + sep.bytesize >= limit
            stop = pos + limit
          else
            stop += sep.bytesize
          end
          line = string.byteslice(pos, stop - pos)
          d.pos = stop
        else
          if limit
            line = string.byteslice(pos, limit)
          else
            line = string.byteslice(pos, bytesize - pos)
          end
          d.pos += line.bytesize
        end
      end

      d.lineno += 1
    end

    if chomp
      line.chomp!(sep || DEFAULT_RECORD_SEPARATOR)
    end

    line
  end

  def marshal_dump
    raise TypeError, "can't dump #{Primitive.class(self)}"
  end
end
