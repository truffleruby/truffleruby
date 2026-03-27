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

module Truffle

  # :internal:
  #
  # The virtual concatenation file of the files given on command line (or
  # from $stdin if no files were given.)
  #
  # The only instance hereof is the object referred to by ARGF.
  #
  # @see  ARGF
  #
  class ARGFClass
    include Enumerable

    attr_reader :argv

    # :internal:
    #
    # Create a stateless ARGF.
    #
    # The actual setup is done on the fly:
    #
    # @see  #advance!
    #
    def initialize(argv = ARGV, *others)
      @argv = Primitive.equal?(argv, ARGV) ? ARGV : [argv, *others]
      @lineno = 0
      # Setting $. sets ARGF.last_lineno, but does not set
      # ARGF.lineno, Almost every operation that sets lineno also sets
      # last_lineno to match. This behaviour is intended to match
      # `io.c` in the MRI implementation.
      @last_lineno = 0
      @advance = true
      @init = false
      @use_stdin_only = false
      @encoding_args = nil
      @backup_filename = nil
      @external = nil
    end

    #
    # Set stream into binary mode.
    #
    # Stream is set into binary mode, i.e. 8-bit ASCII.
    # Once set, the binary mode cannot be undone. Returns
    # self.
    #
    def binmode
      @binmode = true
      set_encoding(Encoding::BINARY)
      self
    end

    def binmode?
      @binmode
    end

    #
    # Close stream.
    #
    def close
      advance!
      @stream.close unless @stream.to_io == $stdin
      @advance = true unless @use_stdin_only
      @lineno = 0
      @last_lineno = 0
      @binmode = false
      self
    end

    #
    # True if the stream is closed.
    #
    def closed?
      advance!
      @stream.closed?
    end

    def default_value
      ''.encode(external_encoding)
    end

    #
    # Linewise iteration.
    #
    # Yields one line from stream at a time, as given by
    # #gets. An Enumerator is returned if no block is
    # provided. Returns nil if no content, self otherwise.
    #
    # @see  #gets.
    #
    def each_line(sep = $/)
      return to_enum :each_line, sep unless block_given?
      return nil unless advance!

      while line = gets(sep)
        yield line
      end
      self
    end
    alias_method :lines, :each_line
    alias_method :each, :each_line

    #
    # Bytewise iteration.
    #
    # Yields one byte at a time from stream, an Integer
    # as given by #getc. An Enumerator is returned if no
    # block is provided. Returns self.
    #
    # @see  #getc
    #
    def each_byte
      return to_enum :each_byte unless block_given?
      while ch = getbyte()
        yield ch
      end
      self
    end
    alias_method :bytes, :each_byte

    #
    # Character-wise iteration.
    #
    # Yields one character at a time from stream. An
    # Enumerator is returned if no block is provided.
    # Returns self.
    #
    # The characters yielded are gotten from #getc.
    #
    # @see  #getc
    #
    def each_char
      return to_enum :each_char unless block_given?
      while c = getc()
        yield c.chr
      end
      self
    end
    alias_method :chars, :each_char

    def each_codepoint
      return to_enum :each_codepoint unless block_given?

      while c = getc
        yield c.ord
      end

      self
    end
    alias_method :codepoints, :each_codepoint

    def external_encoding
      @external || Encoding.default_external
    end

    def internal_encoding
      @internal || Encoding.default_internal
    end

    #
    # Query whether stream is at end-of-file.
    #
    # True if there is a stream and it is in EOF
    # status.
    #
    def eof?
      @stream and @stream.eof?
    end
    alias_method :eof, :eof?

    #
    # File descriptor number for stream.
    #
    # Returns a file descriptor number for the stream being
    # read out of.
    #
    # @todo   Check correctness, does this imply there may be
    #         multiple FDs and if so, is this correct? --rue
    #
    def fileno
      raise ArgumentError, 'No stream' unless advance!
      @stream.fileno
    end
    alias_method :to_i, :fileno

    #
    # File path currently in use.
    #
    # Path to file from which read currently is
    # occurring, or an indication that the stream
    # is STDIN.
    #
    def filename
      advance!
      @filename
    end
    alias_method :path, :filename

    #
    # Current stream object.
    #
    # This may change during the course of execution,
    # but is the current one!
    #
    def file
      advance!
      @stream
    end

    def getbyte
      while advance!
        if val = @stream.getbyte
          return val
        end

        return nil if @use_stdin_only
        @stream.close unless @stream.closed?
        @advance = true
      end
    end

    #
    # Return one character from stream.
    #
    # If a character cannot be returned and we are
    # reading from a file, the stream is closed.
    #
    def getc
      while advance!
        if val = @stream.getc
          return val
        end

        return nil if @use_stdin_only
        @stream.close unless @stream.closed?
        @advance = true
      end
    end

    #
    # Return next line of text from stream.
    #
    # If a line cannot be returned and we are
    # reading from a file, the stream is closed.
    #
    # The mechanism does track the line numbers,
    # and updates $. accordingly.
    #
    def gets(...)
      while advance!
        line = @stream.gets(...)

        unless line
          return nil if @use_stdin_only
          @stream.close unless @stream.closed?
          @advance = true
          next
        end

        Primitive.io_last_line_set(Primitive.caller_special_variables, line) if line
        @last_lineno = @lineno += 1
        return line
      end
    end

    #
    # Return current line number.
    #
    # Line numbers are maintained when using the linewise
    # access methods.
    #
    # @see  #gets
    # @see  #each_line
    #
    attr_reader :lineno

    #
    # Set current line number.
    #
    # Also sets $. accordingly.
    #
    # @todo Should this be public? --rue
    #
    def lineno=(val)
      @lineno = val
      @last_lineno = val
    end

    #
    # Return stream position for seeking etc.
    #
    # @see IO#pos.
    #
    def pos
      raise ArgumentError, 'no stream' unless advance!
      @stream.tell
    end
    alias_method :tell, :pos

    #
    # Set stream position to a previously obtained position.
    #
    # @see IO#pos=
    #
    def pos=(position)
      raise ArgumentError, 'no stream' unless advance!
      @stream.pos = position
    end

    #
    # Read a byte from stream.
    #
    # Similar to #getc, but raises an EOFError if
    # EOF has been reached.
    #
    # @see  #getc
    #
    def readbyte
      advance!

      if val = getc()
        return val
      end

      raise EOFError, 'ARGF at end'
    end
    alias_method :readchar, :readbyte

    #
    # Read number of bytes or all, optionally into buffer.
    #
    # If number of bytes is not given or is nil, tries to read
    # all of the stream, which is then closed. If the number is
    # specified, then at most that many bytes will be read.
    #
    # A buffer responding to #<< may be provided as the second
    # argument. The data read is pushed into it. If no buffer
    # is provided, as by default, a String with the data is
    # returned instead.
    #
    def read(bytes = nil, output = nil)
      # The user might try to pass in nil, so we have to check here
      if Primitive.nil? output
        output = default_value
      else
        output = Primitive.convert_with_to_str(output)
        output.clear
      end

      if bytes
        bytes_left = bytes

        until bytes_left == 0
          return output unless advance!

          if res = @stream.read(bytes_left)
            output << res
            bytes_left -= res.size
          else
            break if @use_stdin_only
            @stream.close unless @stream.closed?
            @advance = true
          end

        end

        return output
      end

      while advance!
        output << @stream.read

        break if @use_stdin_only
        @stream.close unless @stream.closed?
        @advance = true
      end

      output
    end

    def getpartial(bytes, output, is_blocking, exception: true)
      # The user might try to pass in nil, so we have to check here
      if Primitive.nil? output
        output = default_value
      else
        output = Primitive.convert_with_to_str(output)
        output.clear
      end

      raise EOFError unless advance!

      res = begin
              if is_blocking
                @stream.readpartial(bytes)
              else
                @stream.read_nonblock(bytes, exception: exception)
              end
            rescue EOFError
              if @argv.empty?
                raise
              else
                nil
              end
            end

      if res
        return res if !exception && res == :wait_readable
        output << res
      else
        unless @use_stdin_only
          @stream.close unless @stream.closed?
          @advance = true
        end
      end

      output
    end
    private :getpartial

    def readpartial(bytes, output = nil)
      getpartial(bytes, output, true)
    end

    def read_nonblock(bytes, output = nil, exception: true)
      getpartial(bytes, output, false, exception: exception)
    end

    #
    # Read next line of text.
    #
    # As #gets, but an EOFError is raised if the stream
    # is at EOF.
    #
    # @see  #gets
    #
    def readline(sep = $/)
      raise EOFError, 'ARGF at end' unless advance!

      if line = gets(sep)
        return line
      end

      raise EOFError, 'ARGF at end'
    end

    #
    # Read all lines from stream.
    #
    # Reads all lines into an Array using #gets and
    # returns the Array.
    #
    # @see  #gets
    #
    def readlines(sep = $/)
      return [] unless advance!

      lines = []
      while line = gets(sep)
        lines << line
      end

      lines
    end

    alias_method :to_a, :readlines

    #
    # Rewind the stream to its beginning.
    #
    # Line number is updated accordingly.
    #
    # @todo Is this correct, only current stream is rewound? --rue
    #
    def rewind
      raise ArgumentError, 'no stream to rewind' unless advance!
      @lineno -= @stream.lineno
      @stream.rewind
    end

    #
    # Seek into a previous position in the stream.
    #
    # @see IO#seek.
    #
    def seek(*args)
      raise ArgumentError, 'no stream' unless advance!
      @stream.seek(*args)
    end

    def set_encoding(*args)
      advance! unless @stream
      @stream.set_encoding(*args)

      @encoding_args = args
      @internal = @stream.internal_encoding
      @external = @stream.external_encoding
    end

    #
    # Close file stream and return self.
    #
    # STDIN is not closed if being used, otherwise the
    # stream gets closed. Returns self.
    #
    def skip
      return self if @use_stdin_only || Primitive.nil?(@stream)
      @stream.close unless @stream.closed?
      @advance = true
      self
    end

    def stream(file)
      stream = file == '-' ? $stdin : File.open(file, 'r', :external_encoding => external_encoding)

      if @encoding_args
        stream.set_encoding(*@encoding_args)
      elsif external_encoding
        stream.set_encoding external_encoding
      end

      stream
    end

    #
    # Return IO object for current stream.
    #
    # @see IO#to_io
    #
    def to_io
      advance!
      @stream.to_io
    end

    #
    # Returns "ARGF" as the string representation of this object.
    #
    def to_s
      +'ARGF'
    end


    # Internals

    #
    # Main processing.
    #
    # If not initialised yet, sets the object up on either
    # first of provided file names or STDIN.
    #
    # Does nothing further or later if using STDIN, but if
    # there are further file names in ARGV, tries to open
    # the next one as the current stream.
    #
    def advance!
      return true unless @advance

      unless @init

        if @argv.empty?
          @advance = false
          @stream = $stdin
          @filename = +'-'
          @use_stdin_only = true
          return true
        end
        @init = true
      end

      File.unlink(@backup_filename) if @backup_filename && $-i == ''

      return false if @use_stdin_only || @argv.empty?

      @advance = false

      file = @argv.shift
      @stream = stream(file)
      @filename = file

      if $-i && @stream != $stdin
        backup_extension = $-i == '' ? '.bak' : $-i
        @backup_filename = "#{@filename}#{backup_extension}"
        File.rename(@filename, @backup_filename)
        @stream = File.open(@backup_filename, 'r')
        $stdout = File.open(@filename, 'w')
      end

      true
    end
    private :advance!
  end
end

#
# The virtual concatenation file of the files given on command line (or
# from $stdin if no files were given.) Usable like an IO.
#
ARGF = Truffle::ARGFClass.new(ARGV)
Truffle::KernelOperations.define_read_only_global(:$<, -> { ARGF } )
Truffle::KernelOperations.define_read_only_global(:$FILENAME, -> { ARGF.filename } )
Truffle::KernelOperations.define_hooked_variable(
  :$.,
  -> { ARGF.instance_variable_get(:@last_lineno) },
  -> _, value { value = Primitive.convert_with_to_int(value)
                ARGF.instance_variable_set(:@last_lineno, value) } )
