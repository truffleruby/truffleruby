# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
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

class Encoding
  class UndefinedConversionError < EncodingError
    attr_accessor :source_encoding_name
    attr_accessor :destination_encoding_name
    attr_accessor :source_encoding
    attr_accessor :destination_encoding
    attr_accessor :error_char

    private :source_encoding_name=
    private :destination_encoding_name=
    private :source_encoding=
    private :destination_encoding=
    private :error_char=
  end

  class InvalidByteSequenceError < EncodingError
    attr_accessor :source_encoding_name
    attr_accessor :destination_encoding_name
    attr_accessor :source_encoding
    attr_accessor :destination_encoding
    attr_accessor :error_bytes
    attr_accessor :readagain_bytes
    attr_writer :incomplete_input

    private :source_encoding_name=
    private :destination_encoding_name=
    private :source_encoding=
    private :destination_encoding=
    private :error_bytes=
    private :readagain_bytes=
    private :incomplete_input=

    def initialize(message = '')
      super(message)

      @incomplete_input = nil
    end

    def incomplete_input?
      @incomplete_input
    end
  end

  class ConverterNotFoundError < EncodingError
  end

  class CompatibilityError < EncodingError
  end

  class Converter
    attr_accessor :source_encoding
    attr_accessor :destination_encoding

    def self.asciicompat_encoding(string_or_encoding)
      encoding = Encoding.try_convert(string_or_encoding)

      return unless encoding
      return if encoding.ascii_compatible?

      name = encoding.name.upcase.to_sym
      transcoders = Primitive.encoding_transcoders_from_encoding name

      return unless transcoders and transcoders.size == 1

      enc = Encoding.find transcoders[0].to_s
      enc if enc.ascii_compatible?
    end

    def self.search_convpath(from, to, options = 0)
      new(from, to, options).convpath
    end

    def initialize(from, to, options = 0)
      @source_encoding = Truffle::Type.coerce_to_encoding from
      @destination_encoding = Truffle::Type.coerce_to_encoding to

      if Primitive.is_a?(options, Integer)
        @options = options
      else
        options = Primitive.convert_with_to_hash options

        @options = 0
        unless options.empty?
          @options |= INVALID_REPLACE if options[:invalid] == :replace
          @options |= UNDEF_REPLACE if options[:undef] == :replace

          if options[:newline] == :universal or options[:universal_newline]
            @options |= UNIVERSAL_NEWLINE_DECORATOR
          end

          if options[:newline] == :crlf or options[:crlf_newline]
            @options |= CRLF_NEWLINE_DECORATOR
          end

          if options[:newline] == :cr or options[:cr_newline]
            @options |= CR_NEWLINE_DECORATOR
          end

          @options |= XML_TEXT_DECORATOR if options[:xml] == :text
          if options[:xml] == :attr
            @options |= XML_ATTR_CONTENT_DECORATOR
            @options |= XML_ATTR_QUOTE_DECORATOR
          end

          new_replacement = options[:replace]
        end
      end

      @convpath = initialize_jcodings(@source_encoding, @destination_encoding, @options)

      unless @convpath
        conversion = "(#{@source_encoding.name} to #{@destination_encoding.name})"
        msg = "code converter not found #{conversion}"
        raise ConverterNotFoundError, msg
      end

      if (@options & (INVALID_REPLACE | UNDEF_REPLACE | UNDEF_HEX_CHARREF))
        unless Primitive.nil? new_replacement
          new_replacement = Primitive.convert_with_to_str new_replacement
          self.replacement = new_replacement # We can only call `self.replacement=` after the converter has been initialized.
        end
      end
    end

    def convert(str)
      str = Primitive.convert_with_to_str(str)

      dest = +''
      status = primitive_convert str.dup, dest, nil, nil, @options | PARTIAL_INPUT

      if status == :invalid_byte_sequence or
         status == :undefined_conversion or
         status == :incomplete_input
        raise last_error
      end

      if status == :finished
        raise ArgumentError, 'converter already finished'
      end

      if status != :source_buffer_empty
        raise RuntimeError, "unexpected result of Encoding::Converter#primitive_convert: #{status}"
      end

      dest
    end

    def primitive_convert(source, target, offset = nil, size = nil, options = 0)
      source = source ? Primitive.convert_with_to_str(source) : +''
      target = Primitive.convert_with_to_str(target)

      Primitive.check_mutable_string target

      if Primitive.nil? offset
        offset = target.bytesize
      else
        offset = Primitive.convert_with_to_int offset
      end

      if Primitive.nil? size
        size = -1
      else
        size = Primitive.convert_with_to_int size

        if size < 0
          raise ArgumentError, 'byte size is negative'
        end
      end

      if offset < 0
        raise ArgumentError, 'byte offset is negative'
      end

      if offset > target.bytesize
        raise ArgumentError, 'byte offset is greater than destination buffer size'
      end

      unless Primitive.is_a?(options, Integer)
        opts = Primitive.convert_with_to_hash options

        options = 0
        options |= PARTIAL_INPUT if opts[:partial_input]
        options |= AFTER_OUTPUT if opts[:after_output]
      end

      if primitive_errinfo.first == :invalid_byte_sequence
        source.prepend putback
      end

      Primitive.encoding_converter_primitive_convert(self, source, target, offset, size, options)
    end

    def finish
      dest = +''
      status = primitive_convert nil, dest

      if status == :invalid_byte_sequence or
         status == :undefined_conversion or
         status == :incomplete_input
        raise last_error
      end

      if status != :finished
        raise RuntimeError, "unexpected result of Encoding::Converter#finish: #{status}"
      end

      dest
    end

    def last_error
      error = Primitive.encoding_converter_last_error self
      return if Primitive.nil? error

      result, source_encoding_name, destination_encoding_name, error_bytes, read_again_bytes = error
      read_again_string = nil
      codepoint = nil
      error_bytes_msg = error_bytes.dump

      case result
      when :invalid_byte_sequence
        if read_again_string
          msg = "#{error_bytes_msg} followed by #{read_again_string.dump} on #{source_encoding_name}"
        else
          msg = "#{error_bytes_msg} on #{source_encoding_name}"
        end

        exc = InvalidByteSequenceError.new msg
      when :incomplete_input
        msg = "incomplete #{error_bytes_msg} on #{source_encoding_name}"

        exc = InvalidByteSequenceError.new msg
      when :undefined_conversion
        error_char = error_bytes
        if codepoint
          error_bytes_msg = 'U+%04X' % codepoint
        end

        if source_encoding_name.to_sym == @source_encoding.name and
           destination_encoding_name.to_sym == @destination_encoding.name
          msg = "#{error_bytes_msg} from #{source_encoding_name} to #{destination_encoding_name}"
        else
          convpath = @convpath.map { |upcase_name| Encoding.find(upcase_name.to_s).name }
          msg = "#{error_bytes_msg} to #{destination_encoding_name} in conversion from #{convpath.join(' to ')}"
        end

        exc = UndefinedConversionError.new msg
      end

      exc.__send__ :source_encoding_name=, source_encoding_name
      src = Encoding.try_convert(source_encoding_name)
      exc.__send__ :source_encoding=, src unless false == src

      exc.__send__ :destination_encoding_name=, destination_encoding_name
      dst = Encoding.try_convert(destination_encoding_name)
      exc.__send__ :destination_encoding=, dst unless false == dst

      if error_char
        error_char.force_encoding src unless false == src
        exc.__send__ :error_char=, error_char
      end

      if result == :invalid_byte_sequence or result == :incomplete_input
        exc.__send__ :error_bytes=, error_bytes.force_encoding(Encoding::BINARY)

        if bytes = read_again_bytes
          exc.__send__ :readagain_bytes=, bytes.force_encoding(Encoding::BINARY)
        end
      end

      if result == :invalid_byte_sequence
        exc.__send__ :incomplete_input=, false
      elsif result == :incomplete_input
        exc.__send__ :incomplete_input=, true
      end

      exc
    end

    def convpath
      path = []
      a = 0
      b = @convpath.size - 1

      while a < b
        path << [Encoding.find(@convpath[a].to_s), Encoding.find(@convpath[a + 1].to_s)]
        a += 1
      end

      path << 'xml_text_escape' if @options & XML_TEXT_DECORATOR != 0
      path << 'xml_attr_content_escape' if @options & XML_ATTR_CONTENT_DECORATOR != 0
      path << 'xml_attr_quote' if @options & XML_ATTR_QUOTE_DECORATOR != 0
      path << 'universal_newline' if @options & UNIVERSAL_NEWLINE_DECORATOR != 0
      path << 'crlf_newline' if @options & CRLF_NEWLINE_DECORATOR != 0
      path << 'cr_newline' if @options & CR_NEWLINE_DECORATOR != 0

      path
    end

    def inspect
      "#<Encoding::Converter: #{source_encoding.name} to #{destination_encoding.name}>"
    end
  end
end
