# frozen_string_literal: true

# Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
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

# Default Ruby Record Separator
# Used in this file and by various methods that need to ignore $/
DEFAULT_RECORD_SEPARATOR = "\n"

class String
  include Comparable

  def byteslice(index_or_range, length = undefined)
    # Handles the (int index) and (int index, int length) forms.
    str = Primitive.string_byte_substring self, index_or_range, length
    return str unless Primitive.undefined?(str)

    # Convert to (int index, int length) form.
    if Primitive.is_a?(index_or_range, Range) && Primitive.undefined?(length)
      index, length = Primitive.range_normalized_start_length(index_or_range, bytesize)
      return if index < 0 or index > bytesize
      return byteslice 0, 0 if length < 0
    else
      index = Primitive.rb_num2long(index_or_range)
      index += bytesize if index < 0

      if Primitive.undefined?(length)
        return if index == bytesize
        length = 1
      else
        length = Primitive.rb_num2long(length)
        return if length < 0
      end

      length = bytesize unless Primitive.integer_fits_into_int?(index)
      return if index < 0 or index > bytesize
    end

    byteslice index, length
  end

  def bytesplice(index_or_range, *args)
    is_range = Primitive.is_a?(index_or_range, Range)

    length = undefined
    str_index_or_range = undefined
    str_length = undefined
    case args.size
    when 1
      str = args[0]
    when 2
      if is_range
        str, str_index_or_range = args
      else
        length, str = args
      end
    when 4
      length, str, str_index_or_range, str_length = args
    else
      raise ArgumentError, "wrong number of arguments (given #{args.size + 1}, expected 2, 3, or 5)"
    end

    if Primitive.undefined?(length)
      raise TypeError, "wrong argument type #{Primitive.class(index_or_range)} (expected Range)" unless is_range

      start, len = Primitive.range_normalized_start_length(index_or_range, bytesize)
      len = Primitive.max(0, len)
    else
      start = Primitive.rb_to_int(index_or_range)
      start += bytesize if start < 0
      len = Primitive.rb_to_int(length)
    end

    str = StringValue(str)

    if !Primitive.undefined?(str_index_or_range)
      if Primitive.undefined?(str_length)
        if !Primitive.is_a?(str_index_or_range, Range)
          raise TypeError, "wrong argument type #{Primitive.class(str_index_or_range)} (expected Range)"
        end

        str_start, str_len = Primitive.range_normalized_start_length(str_index_or_range, str.bytesize)
        str_len = Primitive.max(0, str_len)
        str_arg_is_range = true
      else
        str_start = Primitive.rb_to_int(str_index_or_range)
        str_start += str.bytesize if str_start < 0
        str_len = Primitive.rb_to_int(str_length)
        str_arg_is_range = false
      end

      if str_len < 0
        raise IndexError, "negative length #{str_length}"
      end

      str_len = Primitive.min(str.bytesize - str_start, str_len)
      Truffle::StringOperations.validate_bytesplice_bounds(str, str_start, str_len, str_index_or_range, str_arg_is_range)

      str = str.byteslice(str_start, str_len)
    end

    if len < 0
      raise IndexError, "negative length #{len}"
    end

    len = Primitive.min(bytesize - start, len)
    Truffle::StringOperations.validate_bytesplice_bounds(self, start, len, index_or_range, is_range)

    Primitive.check_mutable_string(self)
    enc = Primitive.encoding_ensure_compatible_str(self, str)
    Primitive.string_splice(self, str, start, len, enc)
  end

  def self.try_convert(obj)
    Truffle::Type.try_convert obj, String, :to_str
  end

  def =~(pattern)
    case pattern
    when Regexp
      match_data = Primitive.regexp_search(pattern, self, 0)
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, match_data)
      match_data.begin(0) if match_data
    when String
      raise TypeError, 'type mismatch: String given'
    else
      pattern =~ self
    end
  end
  Primitive.always_split self, :=~

  def chomp(separator = $/)
    str = Primitive.dup_as_string_instance(self)
    str.chomp!(separator) || str
  end

  def chop
    str = Primitive.dup_as_string_instance(self)
    str.chop! || str
  end

  def delete(*strings)
    str = Primitive.dup_as_string_instance(self)
    str.delete!(*strings) || str
  end

  def delete_prefix(prefix)
    str = Primitive.dup_as_string_instance(self)
    str.delete_prefix!(prefix) || str
  end

  def delete_prefix!(prefix)
    Primitive.check_mutable_string self
    prefix = Truffle::Type.coerce_to(prefix, String, :to_str)
    if !prefix.empty? && self[0, prefix.size] == prefix
      self[0, prefix.size] = ''
      self
    else
      nil
    end
  end

  def delete_suffix(suffix)
    str = Primitive.dup_as_string_instance(self)
    str.delete_suffix!(suffix) || str
  end

  def delete_suffix!(suffix)
    Primitive.check_mutable_string self
    suffix = Truffle::Type.coerce_to(suffix, String, :to_str)
    if !suffix.empty? && self[-suffix.size, suffix.size] == suffix
      self[size - suffix.size, suffix.size] = ''
      self
    else
      nil
    end
  end

  def grapheme_clusters(&block)
    if block_given?
      each_grapheme_cluster(&block)
    else
      regex = Primitive.regexp_compile('\X'.encode(encoding), 0)
      scan(regex)
    end
  end

  def include?(needle)
    Primitive.as_boolean(Primitive.find_string(self, StringValue(needle), 0))
  end

  def to_sym
    Primitive.string_to_symbol(self, false)
  end
  alias_method :intern, :to_sym

  def lstrip
    str = Primitive.dup_as_string_instance(self)
    str.lstrip! || str
  end

  def oct
    Primitive.string_to_inum(self, -8, false, true)
  end

  # Treats leading characters from <i>self</i> as a string of hexadecimal digits
  # (with an optional sign and an optional <code>0x</code>) and returns the
  # corresponding number. Zero is returned on error.
  #
  #    "0x0a".hex     #=> 10
  #    "-1234".hex    #=> -4660
  #    "0".hex        #=> 0
  #    "wombat".hex   #=> 0
  def hex
    Primitive.string_to_inum(self, 16, false, true)
  end

  def replace(other)
    Primitive.string_replace(self, other)
  end

  def reverse
    Primitive.dup_as_string_instance(self).reverse!
  end

  def partition(pattern = nil)
    return super() if Primitive.nil?(pattern) && block_given?

    if Primitive.is_a?(pattern, Regexp)
      if m = Truffle::RegexpOperations.match(pattern, self)
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, m)
        return [m.pre_match, m.to_s, m.post_match]
      end
    else
      pattern = StringValue(pattern)
      if i = index(pattern)
        post_start = i + pattern.length
        post_len = size - post_start

        return [Primitive.string_substring(self, 0, i),
                pattern,
                Primitive.string_substring(self, post_start, post_len)]
      end
    end

    # Nothing worked out, this is the default.
    empty = String.new(encoding: encoding)
    [Primitive.dup_as_string_instance(self), empty, empty.dup]
  end

  def rpartition(pattern)
    if Primitive.is_a?(pattern, Regexp)
      if m = Primitive.regexp_search_backward(pattern, self, bytesize)
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, m)
        return [m.pre_match, m[0], m.post_match]
      end
    else
      pattern = StringValue(pattern)
      if i = rindex(pattern)
        post_start = i + pattern.length
        post_len = size - post_start

        return [Primitive.string_substring(self, 0, i),
                pattern.dup,
                Primitive.string_substring(self, post_start, post_len)]
      end
    end

    # Nothing worked out, this is the default.
    empty = String.new(encoding: encoding)
    [empty, empty.dup, Primitive.dup_as_string_instance(self)]
  end

  def rstrip
    str = Primitive.dup_as_string_instance(self)
    str.rstrip! || str
  end

  def scan(pattern, &block)
    pattern = Truffle::Type.coerce_to_regexp(pattern, true)
    index = 0

    last_match = nil
    ret = block_given? ? self : []

    while match = Truffle::RegexpOperations.match_from(pattern, self, index)
      fin = Primitive.match_data_byte_end(match, 0)

      if Truffle::RegexpOperations.collapsing?(match)
        if char = Primitive.string_find_character(self, fin)
          index = fin + char.bytesize
        else
          index = fin + 1
        end
      else
        index = fin
      end

      last_match = match
      val = (match.length == 1 ? match[0] : match.captures)

      if block
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, match)
        yield(val)
      else
        ret << val
      end
    end

    Primitive.regexp_last_match_set(Primitive.caller_special_variables, last_match)
    ret
  end
  Primitive.always_split self, :scan

  def split(pattern = nil, limit = undefined, &block)
    Truffle::Splitter.split(Primitive.dup_as_string_instance(self), pattern, limit, &block)
  end
  Primitive.always_split self, :split

  def squeeze(*strings)
    str = Primitive.dup_as_string_instance(self)
    str.squeeze!(*strings) || str
  end

  def strip
    str = Primitive.dup_as_string_instance(self)
    str.strip! || str
  end

  def strip!
    right = rstrip! # Process rstrip! first because it must perform an encoding compatibility check that lstrip! does not.
    left = lstrip!
    Primitive.nil?(left) && Primitive.nil?(right) ? nil : self
  end

  def succ
    Primitive.dup_as_string_instance(self).succ!
  end

  def swapcase!(*options)
    mapped_options = Truffle::StringOperations.validate_case_mapping_options(options, false)
    Primitive.string_swapcase! self, mapped_options
  end

  def swapcase(*options)
    str = Primitive.dup_as_string_instance(self)
    str.swapcase!(*options) || str
  end

  def to_i(base = 10)
    base = Truffle::Type.coerce_to base, Integer, :to_int

    if base < 0 || base == 1 || base > 36
      raise ArgumentError, "illegal radix #{base}"
    end

    Primitive.string_to_inum(self, base, false, true)
  end

  def tr(source, replacement)
    str = Primitive.dup_as_string_instance(self)
    str.tr!(source, replacement) || str
  end
  Primitive.always_split self, :tr

  def tr_s(source, replacement)
    str = Primitive.dup_as_string_instance(self)
    str.tr_s!(source, replacement) || str
  end

  def each_grapheme_cluster
    return to_enum(:each_grapheme_cluster) { size } unless block_given?

    regex = Primitive.regexp_compile('\X'.encode(encoding), 0)
    # scan(regex, &block) would leak the $ vars in the user block which is probably unwanted
    scan(regex) { |e| yield e }
    self
  end

  def encode!(to = undefined, from = undefined, **options)
    Primitive.check_mutable_string self

    if !Primitive.undefined?(to)
      begin
        to_enc = Truffle::Type.coerce_to_encoding(to)
      rescue ArgumentError
        raise Encoding::ConverterNotFoundError, "Encoding #{to} not found."
      end
    else
      to_enc = Encoding.default_internal
    end

    if !Primitive.undefined?(from)
      begin
        from_enc = Truffle::Type.coerce_to_encoding(from)
      rescue ArgumentError
        raise Encoding::ConverterNotFoundError, "Encoding #{from} not found."
      end
    else
      from_enc = encoding
    end

    if ascii_only? and from_enc.ascii_compatible? and to_enc and to_enc.ascii_compatible?
      force_encoding to_enc
    elsif to_enc
      if from_enc != to_enc
        ec = Encoding::Converter.new from_enc, to_enc, **options
        dest = +''
        src = self.dup
        fallback = options[:fallback]
        status = ec.primitive_convert src, dest, nil, nil
        while status != :finished
          raise ec.last_error unless fallback && status == :undefined_conversion
          (_, fallback_enc_from, fallback_enc_to, error_bytes, _) = ec.primitive_errinfo
          rep = fallback[error_bytes.force_encoding(fallback_enc_from)]
          raise ec.last_error unless rep
          rep = Truffle::Type.rb_convert_type rep, String, :to_str
          dest << rep.encode(fallback_enc_to)
          status = ec.primitive_convert src, dest, nil, nil
        end

        return Primitive.string_replace(self, dest)
      else
        force_encoding to_enc
      end
    end

    case options[:invalid]
    when :replace
      replacement = options[:replace] || (Primitive.encoding_is_unicode?(from_enc) ? "\ufffd" : '?')
      self.scrub!(replacement)
    end
    case xml = options[:xml]
    when :text
      gsub!(/[&><]/, '&' => '&amp;', '>' => '&gt;', '<' => '&lt;')
    when :attr
      gsub!(/[&><"]/, '&' => '&amp;', '>' => '&gt;', '<' => '&lt;', '"' => '&quot;')
      insert(0, '"')
      insert(-1, '"')
    when nil
    # nothing
    else
      raise ArgumentError, "unexpected value for xml option: #{xml.inspect}"
    end

    if options[:universal_newline]
      gsub!(/\r\n|\r/, "\r\n" => "\n", "\r" => "\n")
    end

    self
  end

  def b
    dup.force_encoding(Encoding::BINARY)
  end

  def encode(to = undefined, from = undefined, **options)
    dup.encode! to, from, **options
  end

  def end_with?(*suffixes)
    if suffixes.size == 1 and suffix = suffixes[0] and Primitive.is_a?(suffix, String)
      enc = Primitive.encoding_ensure_compatible_str self, suffix
      return Primitive.string_end_with?(self, suffix, enc)
    end

    suffixes.each do |original_suffix|
      suffix = Truffle::Type.rb_convert_type original_suffix, String, :to_str
      enc = Primitive.encoding_ensure_compatible_str self, suffix
      return true if Primitive.string_end_with?(self, suffix, enc)
    end

    false
  end

  def inspect
    result_encoding = Encoding.default_internal || Encoding.default_external
    unless result_encoding.ascii_compatible?
      result_encoding = Encoding::US_ASCII
    end
    # result_encoding is always Encoding#ascii_compatible?

    enc = encoding
    ascii = enc.ascii_compatible?
    unicode = Primitive.encoding_is_unicode? enc

    actual_encoding = Primitive.get_actual_encoding(self)
    if actual_encoding != enc
      enc = actual_encoding
      if unicode
        unicode = Primitive.encoding_is_unicode? enc
      end
    end

    result = '"'.dup.force_encoding(result_encoding)

    index = 0
    total = bytesize
    while index < total
      char = Primitive.string_chr_at(self, index)

      if char
        index += inspect_char(enc, result_encoding, ascii, unicode, index, char, result)
      else
        result << "\\x#{getbyte(index).to_s(16).upcase}"
        index += 1
      end
    end

    result << '"'

    result.force_encoding(result_encoding)
  end

  private def inspect_char(enc, result_encoding, ascii, unicode, index, char, result)
    consumed = char.bytesize
    codepoint = char.ord

    if (ascii or unicode) and (codepoint >= 7 and codepoint <= 92)
      escaped = nil

      case codepoint
      when 7  # \a
        escaped = '\a'
      when 8  # \b
        escaped = '\b'
      when 9  # \t
        escaped = '\t'
      when 10 # \n
        escaped = '\n'
      when 11 # \v
        escaped = '\v'
      when 12 # \f
        escaped = '\f'
      when 13 # \r
        escaped = '\r'
      when 27 # \e
        escaped = '\e'
      when 34 # \"
        escaped = '\"'
      when 35 # #
        case getbyte(index + 1)
        when 36   # $
          escaped = '\#$'
          consumed += 1
        when 64   # @
          escaped = '\#@'
          consumed += 1
        when 123  # {
          escaped = '\#{'
          consumed += 1
        end
      when 92 # \\
        escaped = '\\\\'
      end

      if escaped
        result << escaped
        return consumed
      end
    end

    printable = Primitive.character_printable?(codepoint, enc)
    if printable && (enc == result_encoding || (ascii && char.ascii_only?))
      result << char
    # < 0x7F from https://github.com/ruby/ruby/blob/12f7ba5ed4a07855d6a9429aa627211db3655ca7/string.c#L6049-L6050
    # Exclude UTF-8 (unicode && ascii) because it was already checked just above
    elsif printable && unicode && !ascii && codepoint < 0x7F
      result << codepoint
    else
      escaped = codepoint.to_s(16).upcase

      if unicode
        if codepoint < 0x10000
          pad = '0' * (4 - escaped.bytesize)
          result << "\\u#{pad}#{escaped}"
        else
          result << "\\u{#{escaped}}"
        end
      else
        if codepoint < 0x100
          pad = '0' * (2 - escaped.bytesize)
          result << "\\x#{pad}#{escaped}"
        else
          result << "\\x{#{escaped}}"
        end
      end
    end

    consumed
  end

  def prepend(*others)
    if others.size == 1
      Primitive.string_replace(self, StringValue(others.first) + self)
    else
      reduced = others.reduce(''.encode(self.encoding)) { |memo, other| memo + StringValue(other) }
      Primitive.string_replace(self, StringValue(reduced) + self)
    end
  end

  def upto(stop, exclusive = false)
    return to_enum :upto, stop, exclusive unless block_given?
    stop = StringValue(stop)

    if stop.bytesize == 1 && bytesize == 1 && self.ascii_only? && stop.ascii_only?
      enc = Primitive.encoding_ensure_compatible_str(self, stop)

      return self if self > stop
      after_stop = stop.getbyte(0) + (exclusive ? 0 : 1)
      current = getbyte(0)
      until current == after_stop
        yield current.chr(enc)
        current += 1
      end
    else
      unless stop.size < size
        Primitive.encoding_ensure_compatible_str(self, stop)

        after_stop = exclusive ? stop : stop.succ
        current = self

        begin
          # If both the start and end values are strings representing integer values, the encoding must be US-ASCII.
          Primitive.string_to_inum(self, 10, true, true)
          Primitive.string_to_inum(stop, 10, true, true)
          enc = Encoding::US_ASCII
        rescue ArgumentError
          enc = self.encoding
        end

        until current == after_stop
          yield current
          current = StringValue(current.succ).force_encoding(enc)
          break if current.size > stop.size || current.empty?
        end
      end
    end
    self
  end

  def sub(pattern, replacement = undefined, &block)
    s = Primitive.dup_as_string_instance(self)
    if Primitive.undefined?(replacement) && !block_given?
      raise ArgumentError, "method '#{__method__}': given 1, expected 2"
    end
    Truffle::StringOperations.gsub_internal_core_check_encoding(self)
    matches = Truffle::StringOperations.gsub_internal_matches(false, self, pattern)
    res = Truffle::StringOperations.gsub_match_and_replace(s, matches, replacement, &block)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, matches.last)
    Primitive.string_replace(s, res) if res
    s
  end
  Primitive.always_split self, :sub

  def sub!(pattern, replacement = undefined, &block)
    if Primitive.undefined?(replacement) && !block_given?
      raise ArgumentError, "method '#{__method__}': given 1, expected 2"
    end
    Primitive.check_mutable_string self

    Truffle::StringOperations.gsub_internal_core_check_encoding(self)
    matches = Truffle::StringOperations.gsub_internal_matches(false, self, pattern)
    res = Truffle::StringOperations.gsub_match_and_replace(self, matches, replacement, &block)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, matches.last)
    if res
      Primitive.string_replace(self, res)
      self
    else
      nil
    end
  end
  Primitive.always_split self, :sub!

  def slice!(one, two = undefined)
    Primitive.check_mutable_string self
    # This is un-DRY, but it's a simple manual argument splitting. Keeps
    # the code fast and clean since the sequence are pretty short.
    #
    if Primitive.undefined?(two)
      result = slice(one)

      if Primitive.is_a?(one, Regexp)
        lm = $~
        self[one] = '' if result
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, lm)
      else
        self[one] = '' if result
      end
    else
      result = slice(one, two)

      if Primitive.is_a?(one, Regexp)
        lm = $~
        self[one, two] = '' if result
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, lm)
      else
        self[one, two] = '' if result
      end
    end

    result
  end

  alias_method :next, :succ
  alias_method :next!, :succ!

  def to_c
    Complexifier.new(self).convert
  end

  def to_r
    Rationalizer.new(self).convert
  end

  def chop!
    Primitive.check_mutable_string self

    bytes = Primitive.string_previous_byte_index(self, bytesize)
    return unless bytes

    chr = Primitive.string_chr_at(self, bytes)
    if chr.ord == 10
      if i = Primitive.string_previous_byte_index(self, bytes)
        chr = Primitive.string_chr_at(self, i)

        bytes = i if chr.ord == 13
      end
    end

    Primitive.string_truncate(self, bytes)

    self
  end

  def chomp!(sep = undefined)
    Primitive.check_mutable_string self

    if Primitive.undefined?(sep)
      sep = $/
    elsif sep
      sep = StringValue(sep)
    end

    return if Primitive.nil? sep

    if sep == DEFAULT_RECORD_SEPARATOR
      return unless bytes = Primitive.string_previous_byte_index(self, bytesize)

      chr = Primitive.string_chr_at(self, bytes)

      case chr.ord
      when 13
        # do nothing
      when 10
        if j = Primitive.string_previous_byte_index(self, bytes)
          chr = Primitive.string_chr_at(self, j)

          if !Primitive.nil?(chr) && chr.ord == 13
            bytes = j
          end
        end
      else
        return
      end
    elsif sep.empty?
      return if empty?
      bytes = bytesize

      while i = Primitive.string_previous_byte_index(self, bytes)
        chr = Primitive.string_chr_at(self, i)
        break unless !Primitive.nil?(chr) && chr.ord == 10

        bytes = i

        if j = Primitive.string_previous_byte_index(self, i)
          chr = Primitive.string_chr_at(self, j)
          if !Primitive.nil?(chr) && chr.ord == 13
            bytes = j
          end
        end
      end

      return if bytes == bytesize
    else
      sep_bytesize = sep.bytesize
      return if sep_bytesize > bytesize

      return unless self.end_with?(sep)
      bytes = bytesize - sep_bytesize
    end

    Primitive.string_truncate(self, bytes)

    self
  end

  def chr
    Primitive.string_substring(self, 0, 1)
  end

  def each_line(sep = $/, chomp: false)
    unless block_given?
      return to_enum(:each_line, sep, chomp: chomp)
    end

    # weird edge case.
    if Primitive.nil? sep
      yield Primitive.dup_as_string_instance(self)
      return self
    end

    maybe_chomp = ->(str) { chomp ? str.chomp(sep) : str }

    sep = StringValue(sep)
    if Primitive.equal?(sep, $/) && !self.encoding.ascii_compatible?
      sep = sep.encode(self.encoding)
    else
      Primitive.encoding_ensure_compatible_str(self, sep)
    end

    pos = 0

    duped = dup

    # If the separator is empty, we're actually in paragraph mode. This
    # is used so infrequently, we'll handle it completely separately from
    # normal line breaking.
    if sep.empty?
      sep = "\n\n"
      data = bytes

      while pos < bytesize
        nxt = Primitive.find_string(self, sep, pos)
        break unless nxt

        while data[nxt] == 10 and nxt < bytesize
          nxt += 1
        end

        match_size = nxt - pos

        # string ends with \n's
        break if pos == bytesize

        str = byteslice pos, match_size
        yield Primitive.dup_as_string_instance(maybe_chomp.call(str)) unless str.empty?

        # detect mutation within the block
        if duped != self
          raise RuntimeError, 'string modified while iterating'
        end

        pos = nxt
      end

      # No more separates, but we need to grab the last part still.
      fin = byteslice pos, bytesize - pos
      yield Primitive.dup_as_string_instance(maybe_chomp.call(fin)) if fin and !fin.empty?
    else
      # This is the normal case.
      pat_size = sep.bytesize
      unmodified_self = Primitive.dup_as_string_instance(self)

      while pos < bytesize
        nxt = Primitive.find_string(unmodified_self, sep, pos)
        break unless nxt

        match_size = nxt - pos
        str = unmodified_self.byteslice pos, match_size + pat_size
        yield Primitive.dup_as_string_instance(maybe_chomp.call(str)) unless str.empty?

        pos = nxt + pat_size
      end

      # No more separates, but we need to grab the last part still.
      fin = unmodified_self.byteslice pos, bytesize - pos
      yield Primitive.dup_as_string_instance(maybe_chomp.call(fin)) unless fin.empty?
    end

    self
  end

  def lines(sep = $/, chomp: false, &block)
    if block_given?
      each_line(sep, chomp: chomp, &block)
    else
      each_line(sep, chomp: chomp).to_a
    end
  end


  def gsub(pattern, replacement = undefined, &block)
    s = Primitive.dup_as_string_instance(self)
    if Primitive.undefined?(replacement) && !block_given?
      return s.to_enum(:gsub, pattern, replacement)
    end
    Truffle::StringOperations.gsub_internal_core_check_encoding(self)
    matches = Truffle::StringOperations.gsub_internal_matches(true, self, pattern)
    res = Truffle::StringOperations.gsub_match_and_replace(s, matches, replacement, &block)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, matches.last)
    Primitive.string_replace(s, res) if res
    s
  end
  Primitive.always_split self, :gsub

  def gsub!(pattern, replacement = undefined, &block)
    if Primitive.undefined?(replacement) && !block_given?
      return to_enum(:gsub!, pattern, replacement)
    end
    Primitive.check_mutable_string self

    Truffle::StringOperations.gsub_internal_core_check_encoding(self)
    matches = Truffle::StringOperations.gsub_internal_matches(true, self, pattern)
    res = Truffle::StringOperations.gsub_match_and_replace(self, matches, replacement, &block)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, matches.last)
    if res
      Primitive.string_replace(self, res)
      self
    else
      nil
    end
  end
  Primitive.always_split self, :gsub!

  def match(pattern, pos = 0)
    pattern = Truffle::Type.coerce_to_regexp(pattern) unless Primitive.is_a?(pattern, Regexp)

    result = if block_given?
               pattern.match self, pos do |match|
                 yield match
               end
             else
               pattern.match self, pos
             end
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, $~)
    result
  end
  Primitive.always_split self, :match

  def match?(pattern, pos = 0)
    pattern = Truffle::Type.coerce_to_regexp(pattern) unless Primitive.is_a?(pattern, Regexp)
    pattern.match? self, pos
  end
  Primitive.always_split self, :match?

  def scrub(replace = nil, &block)
    return Primitive.dup_as_string_instance(self) if valid_encoding?

    if !replace and !block
      # The unicode replacement character or '?''
      begin
        replace = "\xEF\xBF\xBD".encode(self.encoding, :undef => :replace, :replace => '?')
      rescue Encoding::ConverterNotFoundError
        replace = '?'.encode(encoding)
      end
    end

    validate = -> str {
      str = StringValue(str)
      unless str.valid_encoding?
        raise ArgumentError, 'replacement must be valid byte sequence'
      end
      # Special encoding check for #scrub
      if str.ascii_only? ? !encoding.ascii_compatible? : encoding != str.encoding
        raise Encoding::CompatibilityError, 'incompatible character encodings'
      end
      str
    }

    if replace
      replace = validate.call(replace)
      replace_block = Proc.new { |_broken| replace }
    else
      replace_block = Proc.new do |broken|
        validate.call(block.call(broken))
      end
    end

    Primitive.string_scrub(self, replace_block)
  end
  Primitive.always_split self, :scrub

  def scrub!(replace = nil, &block)
    return self if valid_encoding?
    Primitive.string_replace(self, scrub(replace, &block))
    self
  end

  def []=(index, count_or_replacement, replacement = undefined)
    Primitive.check_mutable_string self

    if Primitive.undefined?(replacement)
      replacement = count_or_replacement
      count = nil
    else
      count = count_or_replacement
    end

    case index
    when Integer
      Truffle::StringOperations.assign_index(self, index, count, replacement)
    when String
      Truffle::StringOperations.assign_string(self, index, replacement)
    when Range
      Truffle::StringOperations.assign_range(self, index, replacement)
    when Regexp
      Truffle::StringOperations.assign_regexp(self, index, count, replacement)
    else
      index = Primitive.rb_to_int index

      if count
        return self[index, count] = replacement
      else
        return self[index] = replacement
      end
    end

    replacement
  end

  def center(width, padding = ' ')
    padding = StringValue(padding)
    raise ArgumentError, 'zero width padding' if padding.empty?

    Primitive.encoding_ensure_compatible_str self, padding

    width = Primitive.rb_to_int width
    pad = width - size
    return Primitive.dup_as_string_instance(self) if pad <= 0

    rjust(size + pad / 2, padding).ljust(width, padding)
  end

  def ljust(width, padding = ' ')
    padding = StringValue(padding)
    raise ArgumentError, 'zero width padding' if padding.empty?

    enc = Primitive.encoding_ensure_compatible_str self, padding

    width = Primitive.rb_to_int width
    pad = width - size
    return Primitive.dup_as_string_instance(self) if pad <= 0

    result = Primitive.dup_as_string_instance(self).force_encoding(enc)
    whole, remaining = pad.divmod(padding.size)
    result << (padding * whole)
    result << padding[0, remaining] if remaining > 0
    result
  end

  def rjust(width, padding = ' ')
    padding = StringValue(padding)
    raise ArgumentError, 'zero width padding' if padding.empty?

    enc = Primitive.encoding_ensure_compatible_str self, padding

    width = Primitive.rb_to_int width
    pad = width - size
    return Primitive.dup_as_string_instance(self) if pad <= 0

    result = Primitive.dup_as_string_instance('').force_encoding(enc)
    whole, remaining = pad.divmod(padding.size)
    result << (padding * whole)
    result << padding[0, remaining] if remaining > 0
    result << self
    result
  end

  def index(str, start = undefined)
    if Primitive.undefined?(start)
      start = 0
    else
      start = Primitive.rb_to_int start

      start += size if start < 0
      if start < 0 or start > size
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil) if Primitive.is_a?(str, Regexp)
        return
      end
    end

    if Primitive.is_a?(str, Regexp)
      Primitive.regexp_check_encoding(str, self)

      start = Primitive.character_index_to_byte_index(self, start)
      if match = Truffle::RegexpOperations.match_from(str, self, start)
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, match)
        return match.begin(0)
      else
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil)
        return
      end
    end

    str = StringValue(str)
    return start if str == ''

    enc = Primitive.encoding_ensure_compatible_str self, str

    return if start + str.size > size

    Primitive.string_character_index(self, str, enc, start)
  end

  def initialize(other = undefined, capacity: nil, encoding: nil)
    unless Primitive.undefined?(other)
      Primitive.check_mutable_string self
      Primitive.string_initialize(self, other, encoding)
    end
    self.force_encoding(encoding) if encoding
    self
  end

  def rindex(sub, finish = undefined)
    if Primitive.undefined?(finish)
      finish = size
    else
      finish = Truffle::Type.coerce_to(finish, Integer, :to_int)
      finish += size if finish < 0
      return nil if finish < 0
      finish = size if finish >= size
    end

    byte_finish = Primitive.character_index_to_byte_index(self, finish)

    if Primitive.is_a?(sub, Regexp)
      Primitive.regexp_check_encoding(sub, self)

      match_data = Primitive.regexp_search_backward(sub, self, byte_finish)
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, match_data)
      return match_data.begin(0) if match_data

    else
      needle = StringValue(sub)
      needle_size = needle.size

      # needle is bigger that haystack
      return nil if size < needle_size

      # Boundary case
      return finish if needle.empty?

      Primitive.encoding_ensure_compatible_str self, needle
      if byte_index = Primitive.find_string_reverse(self, needle, byte_finish)
        return Primitive.byte_index_to_character_index(self, byte_index)
      end
    end

    nil
  end

  def append_as_bytes(*args)
    Primitive.check_mutable_string self

    args.each do |arg|
      case arg
      when String
        Primitive.string_byte_append(self, arg)
      when Integer
        Primitive.string_byte_append(self, (arg % 256).chr)
      else
        raise TypeError, "wrong argument type #{Primitive.class(arg).name} (expected String or Integer)"
      end
    end
  end

  def byteindex(str, start = 0)
    is_regex_pattern = Primitive.is_a?(str, Regexp)

    start = Primitive.rb_to_int(start)

    start += bytesize if start < 0
    if start < 0 || start > bytesize
      if is_regex_pattern
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil)
      end

      return nil
    end

    if start < bytesize && !Primitive.string_is_character_head?(encoding, self, start)
      raise IndexError, "offset #{start} does not land on character boundary"
    end

    if is_regex_pattern
      Primitive.regexp_check_encoding(str, self)

      match = Truffle::RegexpOperations.match_from(str, self, start)
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, match)
      return match ? Primitive.match_data_byte_begin(match, 0) : nil
    end

    str = StringValue(str)
    return start if str.empty?
    return nil if start + str.bytesize > bytesize

    enc = Primitive.encoding_ensure_compatible_str(self, str)
    Primitive.string_byte_index(self, str, enc, start)
  end

  def byterindex(str, finish = bytesize)
    finish = Primitive.rb_to_int(finish)
    finish += bytesize if finish < 0
    return nil if finish < 0

    finish = Primitive.min(finish, bytesize)

    if finish < bytesize && !Primitive.string_is_character_head?(encoding, self, finish)
      raise IndexError, "offset #{finish} does not land on character boundary"
    end

    if Primitive.is_a?(str, Regexp)
      Primitive.regexp_check_encoding(str, self)

      match = Primitive.regexp_search_backward(str, self, finish)
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, match)
      return match ? Primitive.match_data_byte_begin(match, 0) : nil
    end

    str = StringValue(str)
    return finish if str.empty?
    return nil if str.bytesize > bytesize

    # To compensate for `LastByteIndexOfStringNode` which does a reverse search with index pointing at the end of the
    # pattern instead of the start.
    finish = Primitive.min(bytesize, finish + str.bytesize)

    enc = Primitive.encoding_ensure_compatible_str(self, str)
    Primitive.string_byte_reverse_index(self, str, enc, finish)
  end

  def start_with?(*prefixes)
    if prefixes.size == 1 and prefix = prefixes[0] and Primitive.is_a?(prefix, String)
      enc = Primitive.encoding_ensure_compatible_str self, prefix
      return Primitive.string_start_with?(self, prefix, enc)
    end

    # This is the workaround because `Primitive.caller_special_variables` doesn't work inside blocks yet.
    storage = Primitive.caller_special_variables if prefixes.any?(Regexp)

    prefixes.each do |original_prefix|
      case original_prefix
      when Regexp
        match_data = Primitive.regexp_match_at_start(original_prefix, self, 0, 0)
        Primitive.regexp_last_match_set(storage, match_data)
        return true if match_data
      else
        prefix = Truffle::Type.rb_convert_type original_prefix, String, :to_str
        enc = Primitive.encoding_ensure_compatible_str self, prefix
        return true if Primitive.string_start_with?(self, prefix, enc)
      end
    end
    false
  end

  def insert(index, other)
    other = StringValue(other)

    index = Primitive.rb_to_int index
    index = length + 1 + index if index < 0

    if index > length or index < 0 then
      raise IndexError, "index #{index} out of string"
    end

    Primitive.check_mutable_string self

    if index == 0
      Primitive.string_replace(self, other + self)
    elsif index == length
      self << other
    else
      left = self[0...index]
      right = self[index..-1]
      Primitive.string_replace(self, left + other + right)
    end

    self
  end

  def %(args)
    if Primitive.is_a?(args, Hash)
      sprintf(self, args)
    else
      result = Truffle::Type.rb_check_convert_type args, Array, :to_ary
      if Primitive.nil? result
        sprintf(self, args)
      else
        sprintf(self, *result)
      end
    end
  end
  Primitive.always_split self, :%

  def capitalize!(*options)
    mapped_options = Truffle::StringOperations.validate_case_mapping_options(options, false)
    Primitive.string_capitalize! self, mapped_options
  end

  def capitalize(*options)
    s = Primitive.dup_as_string_instance(self)
    s.capitalize!(*options)
    s
  end

  def downcase!(*options)
    mapped_options = Truffle::StringOperations.validate_case_mapping_options(options, true)
    Primitive.string_downcase! self, mapped_options
  end

  def downcase(*options)
    s = Primitive.dup_as_string_instance(self)
    s.downcase!(*options)
    s
  end

  def upcase!(*options)
    mapped_options = Truffle::StringOperations.validate_case_mapping_options(options, false)
    Primitive.string_upcase! self, mapped_options
  end

  def upcase(*options)
    s = Primitive.dup_as_string_instance(self)
    s.upcase!(*options)
    s
  end

  def casecmp(other)
    other = Truffle::Type.rb_check_convert_type(other , String, :to_str)
    if Primitive.nil? other
      nil
    else
      Primitive.string_casecmp(self, other)
    end
  end

  def casecmp?(other)
    other = Truffle::Type.rb_check_convert_type(other , String, :to_str)
    return nil if Primitive.nil? other

    enc = Primitive.strings_compatible?(self, other)
    if Primitive.nil? enc
      return nil
    end

    if ascii_only? && other.ascii_only?
      casecmp(other) == 0
    else
      downcase(:fold).casecmp(other.downcase(:fold)) == 0
    end
  end

  def +@
    frozen? ? dup : self
  end

  def -@
    str = frozen? ? self : dup.freeze
    if Primitive.string_interned?(self)
      self
    elsif Primitive.any_instance_variable?(str)
      str
    else
      Primitive.string_intern(str)
    end
  end
  alias_method :dedup, :-@

  def <=>(other)
    if Primitive.is_a?(other, String)
      return Primitive.string_cmp(self, other, Primitive.strings_compatible?(self, other))
    end

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      if other.respond_to?(:<=>) && !other.respond_to?(:to_str)
        return nil unless tmp = (other <=> self)
      elsif other.respond_to?(:to_str)
        return nil unless tmp = (other.to_str <=> self)
      else
        return nil
      end

      # Normalize the results to be in {-1, 0, 1}. Also, since at this point we've inverted the objects being compared,
      # we must now invert the results.
      return -1 if tmp > 0
      return 1  if tmp < 0
      return 0
    end

    nil # Fallback value if recursive calls are detected.
  end

  def crypt(salt)
    salt = StringValue(salt)
    raise ArgumentError, 'salt too short (need >= 2 bytes)' if salt.bytesize < 2 || salt[0] == "\0" || salt[1] == "\0"
    raise ArgumentError, 'string contains null byte' if include?("\0")
    crypted = Truffle::POSIX.crypt(self, salt)
    Errno.handle unless crypted
    crypted
  end

  def unpack(format, offset: undefined)
    format = Truffle::Type.rb_convert_type(format, String, :to_str)
    unless Primitive.undefined?(offset)
      offset = Primitive.rb_num2int(offset) # to guarantee it's `int` finally
    end
    Primitive.string_unpack(self, format, offset)
  end
  Primitive.always_split self, :unpack

  def unpack1(format, offset: undefined)
    unpack(format, offset: offset).first
  end
  Primitive.always_split self, :unpack1

  def unicode_normalize(form = :nfc)
    require 'unicode_normalize/normalize.rb' unless defined? UnicodeNormalize
    UnicodeNormalize.normalize(self, form)
  end

  def unicode_normalize!(form = :nfc)
    Primitive.string_replace(self, unicode_normalize(form))
  end

  def unicode_normalized?(form = :nfc)
    require 'unicode_normalize/normalize.rb' unless defined? UnicodeNormalize
    UnicodeNormalize.normalized?(self, form)
  end
end
