# frozen_string_literal: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
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

class Regexp
  IGNORECASE         = 1
  EXTENDED           = 2
  MULTILINE          = 4
  FIXEDENCODING      = 16
  NOENCODING         = 32
  DONT_CAPTURE_GROUP = 128
  CAPTURE_GROUP      = 256

  KCODE_NONE = (1 << 9)
  KCODE_EUC  = (2 << 9)
  KCODE_SJIS = (3 << 9)
  KCODE_UTF8 = (4 << 9)
  KCODE_MASK = KCODE_NONE | KCODE_EUC | KCODE_SJIS | KCODE_UTF8

  OPTION_MASK = IGNORECASE | EXTENDED | MULTILINE | FIXEDENCODING | NOENCODING | DONT_CAPTURE_GROUP | CAPTURE_GROUP

  def self.try_convert(obj)
    Truffle::Type.try_convert obj, Regexp, :to_regexp
  end

  def self.negotiate_union_encoding(*patterns)
    compatible_enc = nil

    patterns.each do |pattern|
      converted = Primitive.is_a?(pattern, Regexp) ? pattern : Regexp.quote(pattern)

      enc = converted.encoding

      if Primitive.nil?(compatible_enc)
        compatible_enc = enc
      else
        if test = Primitive.encoding_compatible?(enc, compatible_enc)
          compatible_enc = test
        else
          raise ArgumentError, "incompatible encodings: #{compatible_enc} and #{enc}"
        end

      end
    end

    compatible_enc
  end

  def self.last_match(index = nil)
    match = Primitive.regexp_last_match_get(Primitive.caller_special_variables)
    if index
      if match
        # cannot delegate all parameter conversion to MatchData#[] due to differing parameter types allowed
        case index
        when Symbol, String
          # no conversion needed
        else
          index = Primitive.rb_to_int index
        end
        match[index]
      else
        nil
      end
    else
      match
    end
  end

  def self.linear_time?(regexp, options = undefined)
    if Primitive.is_a?(regexp, Regexp)
      unless Primitive.undefined?(options)
        warn('flags ignored', uplevel: 1)
      end
    else
      if Primitive.undefined?(options)
        options = 0
      end

      regexp = Regexp.new(regexp, options) # expect String source to be passed
    end

    Truffle::RegexpOperations.linear_time?(regexp)
  end

  def self.union(*patterns)
    case patterns.size
    when 0
      %r/(?!)/
    when 1
      pattern = patterns.first
      case pattern
      when Array
        union(*pattern)
      else
        converted = Truffle::Type.rb_check_convert_type(pattern, Regexp, :to_regexp)
        if Primitive.nil? converted
          Primitive.regexp_compile(Regexp.quote(pattern), 0)
        else
          converted
        end
      end
    else
      patterns = patterns.map do |pat|
        if Primitive.is_a?(pat, Regexp)
          pat
        else
          StringValue(pat)
        end
      end

      enc = negotiate_union_encoding(*patterns)
      sep = '|'.encode(enc)
      str = ''.encode(enc)

      Truffle::RegexpOperations.union(str, sep, *patterns)
    end
  end
  Primitive.always_split singleton_class, :union

  def self.new(pattern, opts = undefined, encoding = nil)
    if Primitive.is_a?(pattern, Regexp)
      warn 'flags ignored' unless Primitive.undefined?(opts)
      opts = pattern.options
      pattern = pattern.source
    else
      pattern = Truffle::Type.rb_convert_type pattern, String, :to_str
    end

    if Primitive.is_a?(opts, Integer)
      opts = opts & (OPTION_MASK | KCODE_MASK) if opts > 0
    elsif Primitive.is_a?(opts, String)
      opts = opts.chars.reduce(0) do |result, char|
        case char
        when 'i'
          result | IGNORECASE
        when 'm'
          result | MULTILINE
        when 'x'
          result | EXTENDED
        else
          raise ArgumentError, "unknown regexp option: #{opts}"
        end
      end
    elsif !Primitive.undefined?(opts)
      # Valid values are true, false, nil.
      # Other values warn but treat as true.
      if Primitive.false?(opts) || Primitive.nil?(opts)
        opts = 0
      else
        warn "expected true or false as ignorecase: #{opts}" unless Primitive.true?(opts)
        opts = IGNORECASE
      end
    else
      opts = 0
    end

    if encoding
      encoding = Truffle::Type.rb_convert_type encoding, String, :to_str
      code = encoding[0]
      if code == ?n or code == ?N
        opts |= NOENCODING
      else
        warn "encoding option is ignored - #{encoding}"
      end
    end

    Primitive.regexp_compile pattern, opts # may be overridden by subclasses
  end
  Primitive.always_split singleton_class, :new

  class << self
    alias_method :compile, :new
  end

  def =~(str)
    result = Truffle::RegexpOperations.match(self, str, 0)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, result)

    result.begin(0) if result
  end
  Primitive.always_split self, :=~

  def match(str, pos = 0)
    result = Truffle::RegexpOperations.match(self, str, pos)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, result)

    if result && block_given?
      yield result
    else
      result
    end
  end
  Primitive.always_split self, :match

  def match?(str, pos = 0)
    Truffle::RegexpOperations.match?(self, str, pos)
  end
  Primitive.always_split self, :match?

  def ===(other)
    if Primitive.is_a?(other, Symbol)
      other = other.to_s
    elsif !Primitive.is_a?(other, String)
      other = Truffle::Type.rb_check_convert_type other, String, :to_str
      unless other
        Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil)
        return false
      end
    end

    if match = Truffle::RegexpOperations.match_from(self, other, 0)
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, match)
      true
    else
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil)
      false
    end
  end
  Primitive.always_split self, :===

  def eql?(other)
    return false unless Primitive.is_a?(other, Regexp)
    return false unless source == other.source
    (options & ~NOENCODING) == (other.options & ~NOENCODING)
  end

  alias_method :==, :eql?

  def inspect
    # the regexp matches any / that is after anything except for a \
    escape = source.gsub(%r!(\\.)|/!) { $1 || '\/' }
    str = "/#{escape}/#{Truffle::RegexpOperations.option_to_string(options)}"
    str << 'n' if (options & NOENCODING) > 0
    str
  end

  def encoding
    Primitive.encoding_get_object_encoding self
  end

  def ~
    line = Primitive.io_last_line_get(Primitive.caller_special_variables)

    unless Primitive.is_a?(line, String)
      Primitive.regexp_last_match_set(Primitive.caller_special_variables, nil)
      return nil
    end

    res = match(line)
    res ? res.begin(0) : nil
  end
  Primitive.always_split self, :~

  def casefold?
    (options & IGNORECASE) > 0 ? true : false
  end

  #
  # call-seq:
  #    rxp.named_captures  => hash
  #
  # Returns a hash representing information about named captures of <i>rxp</i>.
  #
  # A key of the hash is a name of the named captures.
  # A value of the hash is an array which is list of indexes of corresponding
  # named captures.
  #
  #    /(?<foo>.)(?<bar>.)/.named_captures
  #    #=> {"foo"=>[1], "bar"=>[2]}
  #
  #    /(?<foo>.)(?<foo>.)/.named_captures
  #    #=> {"foo"=>[1, 2]}
  #
  # If there are no named captures, an empty hash is returned.
  #
  #    /(.)(.)/.named_captures
  #    #=> {}
  #
  def named_captures
    Hash[Primitive.regexp_names(self)].transform_keys!(&:to_s)
  end

  #
  # call-seq:
  #    rxp.names   => [name1, name2, ...]
  #
  # Returns a list of names of captures as an array of strings.
  #
  #     /(?<foo>.)(?<bar>.)(?<baz>.)/.names
  #     #=> ["foo", "bar", "baz"]
  #
  #     /(?<foo>.)(?<foo>.)/.names
  #     #=> ["foo"]
  #
  #     /(.)(.)/.names
  #     #=> []
  #
  def names
    Primitive.regexp_names(self).map { |x| x.first.to_s }
  end

end
