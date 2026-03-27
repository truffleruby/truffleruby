# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module StringOperations

    # Similar to MRI's Warning::buffer class
    class SimpleStringIO
      attr_reader :string

      def initialize(str)
        @string = str
      end

      def write(*args)
        @string.concat(*args)
      end
    end

    def self.gsub_match_and_replace(orig, matches, replacement, &block)
      if Primitive.undefined?(replacement)
        duped = orig.dup
        gsub_internal_yield_matches(orig, matches) do |_ret, m|
          Primitive.regexp_last_match_set(Primitive.proc_special_variables(block), m)
          val = yield m.to_s
          if duped != orig.dup
            raise RuntimeError, 'string modified'
          end
          val
        end
      else
        unless Primitive.is_a?(replacement, String)
          hash = Truffle::Type.rb_check_convert_type(replacement, Hash, :to_hash)
          replacement = Primitive.convert_with_to_str(replacement) unless hash
        end

        if hash
          gsub_internal_hash(orig, matches, hash)
        else
          gsub_internal_replacement(orig, matches, replacement)
        end
      end
    end

    def self.gsub_internal_hash(orig, matches, replacement)
      gsub_internal_yield_matches(orig, matches) do |_ret, m|
        replacement[m.to_s]
      end
    end

    def self.gsub_internal_replacement(orig, matches, replacement)
      gsub_internal_yield_matches(orig, matches) do |ret, m|
        Truffle::StringOperations.to_sub_replacement(replacement, ret, m)
      end
    end

    def self.gsub_internal_core_check_encoding(orig)
      unless orig.valid_encoding?
        raise ArgumentError, "invalid byte sequence in #{orig.encoding}"
      end
    end

    def self.gsub_internal_matches(global, orig, pattern)
      if Primitive.is_a?(pattern, Regexp)
        gsub_regexp_matches(global, orig, pattern)
      elsif Primitive.is_a?(pattern, String)
        gsub_string_matches(global, orig, pattern)
      else
        gsub_other_matches(global, orig, pattern)
      end
    end
    Primitive.always_split singleton_class, :gsub_internal_matches

    def self.gsub_new_offset(orig, match)
      start_pos = Primitive.match_data_byte_begin(match, 0)
      end_pos = Primitive.match_data_byte_end(match, 0)
      if start_pos == end_pos
        if char = Primitive.string_find_character(orig, start_pos)
          start_pos + char.bytesize
        else
          start_pos + 1
        end
      else
        end_pos
      end
    end

    def self.gsub_regexp_matches(global, orig, pattern)
      res = []
      offset = 0
      while match = Primitive.regexp_search(pattern, orig, offset)
        res << match
        break unless global
        offset = gsub_new_offset(orig, match)
      end
      res
    end

    def self.gsub_string_matches(global, orig, pattern)
      res = []
      offset = 0
      enc = Primitive.encoding_ensure_compatible_str orig, pattern

      while index = byte_index(orig, pattern, enc, offset)
        match = Primitive.matchdata_create_single_group(pattern, orig.dup, index, index + pattern.bytesize)
        res << match
        break unless global
        offset = gsub_new_offset(orig, match)
      end
      res
    end

    def self.gsub_other_matches(global, orig, pattern)
      pattern = Truffle::Type.coerce_to_regexp(pattern, true)
      res = []
      offset = 0
      while match = Primitive.regexp_search(pattern, orig, offset)
        res << match
        break unless global
        offset = gsub_new_offset(orig, match)
      end
      res
    end
    Primitive.always_split singleton_class, :gsub_other_matches

    def self.gsub_internal_yield_matches(orig, matches)
      return nil if matches.empty?

      last_end = 0
      ret = orig.byteslice(0, 0) # Empty string and string subclass

      matches.each do |match|
        str = Truffle::RegexpOperations.pre_match_from(match, last_end)
        Primitive.string_append(ret, str) if str

        val = yield ret, match
        val = val.to_s unless Primitive.is_a?(val, String)
        val = Primitive.rb_any_to_s(val) unless Primitive.is_a?(val, String)

        Primitive.string_append(ret, val)
        last_end = Primitive.match_data_byte_end(match, 0)
      end

      str = orig.byteslice(last_end, orig.bytesize-last_end+1)
      Primitive.string_append(ret, str) if str

      ret
    end

    def self.concat_internal(string, other)
      Primitive.check_frozen string

      unless Primitive.is_a?(other, String)
        if Primitive.is_a?(other, Integer)
          if string.encoding == Encoding::US_ASCII and other >= 128 and other < 256
            string.force_encoding(Encoding::BINARY)
          end

          other = other.chr(string.encoding)
        else
          other = Primitive.convert_with_to_str(other)
        end
      end

      Primitive.string_append(string, other)
    end

    def self.case_mapping_option_to_int(option, downcasing = false)
      case option
      when :ascii
        1 << 22 # CASE_ASCII_ONLY
      when :turkic
        1 << 20 # CASE_FOLD_TURKISH_AZERI
      when :lithuanian
        1 << 21 # CASE_FOLD_LITHUANIAN
      when :fold then
        if downcasing
          1 << 19 # CASE_FOLD
        else
          raise ArgumentError, 'option :fold only allowed for downcasing'
        end
      else
        raise ArgumentError, 'invalid option'
      end
    end

    def self.shorten!(string, size)
      return if string.empty?
      Primitive.string_truncate(string, string.bytesize - size)
    end

    def self.to_sub_replacement(string, result, match)
      index = 0
      while index < string.bytesize
        current = Primitive.find_string(string, '\\', index)
        current = string.bytesize if Primitive.nil? current


        Primitive.string_append(result, string.byteslice(index, current - index))
        break if current == string.bytesize

        # found backslash escape, looking next
        if current == string.bytesize - 1
          Primitive.string_append(result, '\\') # backslash at end of string
          break
        end
        index = current + 1

        cap = string.getbyte(index)

        additional = case cap
                     when 38   # ?&
                       match[0]
                     when 96   # ?`
                       match.pre_match
                     when 39   # ?'
                       match.post_match
                     when 43   # ?+
                       match.captures.compact[-1].to_s
                     when 48..57   # ?0..?9
                       match[cap - 48].to_s
                     when 92 # ?\\ escaped backslash
                       '\\'
                     when 107 # \k named capture
                       if string.getbyte(index + 1) == 60
                         name = +''
                         i = index + 2
                         data = string.bytes
                         while i < string.bytesize && data[i] != 62
                           name << data[i]
                           i += 1
                         end
                         if i >= string.bytesize
                           name << '\\'
                           name << cap.chr
                           index += 1
                           next
                         end
                         index = i
                         name.force_encoding result.encoding
                         match[name]
                       else
                         '\\' + cap.chr
                       end
                     else     # unknown escape
                       '\\' + cap.chr
                     end
        Primitive.string_append(result, additional)
        index += 1
      end
    end

    def self.validate_bytesplice_bounds(str, start, len, index_or_range, is_range)
      bytesize = str.bytesize

      if bytesize < start || start < 0
        if is_range
          raise RangeError, "#{index_or_range} out of range"
        else
          raise IndexError, "index #{index_or_range} out of string"
        end
      end

      encoding = str.encoding

      if start < bytesize && !Primitive.string_is_character_head?(encoding, str, start)
        raise IndexError, "offset #{start} does not land on character boundary"
      end

      finish = start + len

      if finish < bytesize && !Primitive.string_is_character_head?(encoding, str, finish)
        raise IndexError, "offset #{finish} does not land on character boundary"
      end
    end

    def self.validate_case_mapping_options(options, downcasing)
      if options.size > 2
        raise ArgumentError, 'too many options'
      end

      if options.size == 0
        0
      elsif options.size == 1
        case_mapping_option_to_int(options.first, downcasing)
      elsif options.size == 2
        first = options[0]
        second = options[1]

        case first
        when :turkic then
          if second == :lithuanian
            case_mapping_option_to_int(:turkic) | case_mapping_option_to_int(:lithuanian)
          else
            raise ArgumentError, 'invalid second option'
          end
        when :lithuanian
          if second == :turkic
            case_mapping_option_to_int(:lithuanian) | case_mapping_option_to_int(:turkic)
          else
            raise ArgumentError, 'invalid second option'
          end
        else raise ArgumentError, 'too many options'
        end
      end
    end

    # MRI: rb_str_byteindex_m
    def self.byte_index(src, str, enc, start = 0)
      start += src.bytesize if start < 0
      if start < 0 or start > src.bytesize
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil) if Primitive.is_a?(str, Regexp)
        return nil
      end

      return start if str == ''

      Primitive.string_byte_index(src, str, enc, start)
    end

    def self.subpattern(string, pattern, capture)
      match = Truffle::RegexpOperations.match(pattern, string)

      return nil unless match

      if index = Truffle::Type.rb_check_convert_type(capture, Integer, :to_int)
        return nil if index >= match.size || -index >= match.size
        capture = index
      end

      str = match[capture]
      [match, str]
    end

    def self.assign_index(string, index, count, replacement)
      index += string.size if index < 0

      if index < 0 or index > string.size
        raise IndexError, "index #{index} out of string"
      end

      unless bi = Primitive.string_character_index_to_byte_index(string, index)
        raise IndexError, "unable to find character at: #{index}"
      end

      if count
        count = Primitive.convert_with_to_int count

        if count < 0
          raise IndexError, 'count is negative'
        end

        total = index + count
        if total >= string.size
          bs = string.bytesize - bi
        else
          bs = Primitive.string_character_index_to_byte_index(string, total) - bi
        end
      else
        bs = index == string.size ? 0 : Primitive.string_character_index_to_byte_index(string, index + 1) - bi
      end

      replacement = Primitive.convert_with_to_str(replacement)
      enc = Primitive.encoding_ensure_compatible_str string, replacement

      Primitive.string_splice(string, replacement, bi, bs, enc)
    end

    def self.assign_string(string, index, replacement)
      unless start = Primitive.find_string(string, index, 0)
        raise IndexError, 'string not matched'
      end

      replacement = Primitive.convert_with_to_str(replacement)
      enc = Primitive.encoding_ensure_compatible_str string, replacement

      Primitive.string_splice(string, replacement, start, index.bytesize, enc)
    end

    def self.assign_range(string, range, replacement)
      start, length = Primitive.range_normalized_start_length(range, string.size)
      stop = start + length - 1

      raise RangeError, "#{range} out of range" if start < 0 or start > string.size

      bi = Primitive.string_character_index_to_byte_index(string, start)
      raise IndexError, "unable to find character at: #{start}" unless bi

      if stop < start
        bs = 0
      elsif stop >= string.size
        bs = string.bytesize - bi
      else
        bs = Primitive.string_character_index_to_byte_index(string, stop + 1) - bi
      end

      replacement = Primitive.convert_with_to_str(replacement)
      enc = Primitive.encoding_ensure_compatible_str string, replacement

      Primitive.string_splice(string, replacement, bi, bs, enc)
    end

    def self.assign_regexp(string, index, count, replacement)
      if count
        count = Primitive.convert_with_to_int count
      else
        count = 0
      end

      if match = Truffle::RegexpOperations.match(index, string)
        ms = match.size
      else
        raise IndexError, 'regexp does not match'
      end

      count += ms if count < 0 and -count < ms
      unless count < ms and count >= 0
        raise IndexError, "index #{count} out of match bounds"
      end

      unless match[count]
        raise IndexError, "regexp group #{count} not matched"
      end

      replacement = Primitive.convert_with_to_str(replacement)
      enc = Primitive.encoding_ensure_compatible_str string, replacement

      bi = Primitive.string_character_index_to_byte_index(string, match.begin(count))
      bs = Primitive.string_character_index_to_byte_index(string, match.end(count)) - bi

      Primitive.string_splice(string, replacement, bi, bs, enc)
    end
  end
end
