# frozen_string_literal: true
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

# Modifications made by the Truffle team are:
#
# Copyright (c) 2017, 2019 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.


class StringScanner

  Id = 'None$Id'.freeze
  Version = '3.1.3'.freeze

  class Error < StandardError
  end

  attr_reader :pos
  alias_method :pointer, :pos

  def initialize(string, dup = false, fixed_anchor: false)
    if string.instance_of? String
      @original = string
      @string = string
    else
      @original = StringValue(string)
      @string = String.new @original
    end

    reset_state

    @fixed_anchor = Primitive.as_boolean(fixed_anchor)
  end

  def pos=(n)
    n = Integer(n)

    n += @string.bytesize if n < 0

    if n < 0 or n > @string.bytesize
      raise RangeError, "index out of range (#{n})"
    end

    @pos = n
  end
  alias_method :pointer=, :pos=

  def [](n)
    if @match
      raise TypeError, "no implicit conversion of #{Primitive.class(n)} into Integer" if Primitive.is_a?(n, Range)
      @match[n]
    end
  end

  def beginning_of_line?
    @pos == 0 or @string.getbyte(@pos-1) == 10
  end
  alias_method :bol?, :beginning_of_line?

  def captures
    @match&.captures
  end

  def charpos
    @string.byteslice(0, @pos).length
  end

  def check(pattern)
    scan_internal pattern, false, true, true
  end
  Primitive.always_split self, :check

  def check_until(pattern)
    scan_internal pattern, false, true, false
  end
  Primitive.always_split self, :check_until

  def clear
    warn 'StringScanner#clear is obsolete; use #terminate instead' if $VERBOSE
    terminate
  end

  def concat(str)
    @string << StringValue(str)
    self
  end
  alias_method :<<, :concat

  def empty?
    warn 'StringScanner#empty? is obsolete; use #eos? instead?' if $VERBOSE
    eos?
  end

  def eos?
    raise ArgumentError, 'uninitialized StringScanner object' unless @string
    @pos >= @string.bytesize
  end

  def exist?(pattern)
    scan_internal pattern, false, false, false
  end
  Primitive.always_split self, :exist?

  def fixed_anchor?
    @fixed_anchor
  end

  def get_byte
    if eos?
      @match = nil
      return nil
    end

    # We need to match one byte, regardless of the string encoding
    pos = @pos
    @match = Primitive.matchdata_create_single_group(/./mn, @string, pos, pos + 1)

    @prev_pos = pos
    @pos = pos + 1

    @string.byteslice(@prev_pos, 1)
  end

  def getbyte
    warn 'StringScanner#getbyte is obsolete; use #get_byte instead' if $VERBOSE
    get_byte
  end

  def getch
    scan(/./m)
  end

  def inspect
    if defined? @string
      if eos?
        str = "#<#{Primitive.class(self)} fin>"
      else
        pos = @pos
        if string.bytesize - pos > 5
          rest = "#{string[pos..pos+4]}..."
        else
          rest = string[pos..string.bytesize]
        end

        if pos > 0
          if pos > 5
            prev = "...#{string[pos-5...pos]}"
          else
            prev = string[0...pos]
          end

          str = "#<#{Primitive.class(self)} #{pos}/#{string.bytesize} #{prev.inspect} @ #{rest.inspect}>"
        else
          str = "#<#{Primitive.class(self)} #{pos}/#{string.bytesize} @ #{rest.inspect}>"
        end
      end

      str
    else
      "#<#{Primitive.class(self)} (uninitialized)>"
    end
  end

  def match?(pattern)
    scan_internal pattern, false, false, true
  end
  Primitive.always_split self, :match?

  def matched
    @match&.to_s
  end

  def matched?
    Primitive.as_boolean(@match)
  end

  def matched_size
    Primitive.match_data_byte_end(@match, 0) - Primitive.match_data_byte_begin(@match, 0) if @match
  end

  def named_captures
    @match&.named_captures || {}
  end

  def post_match
    @match&.post_match
  end

  def pre_match
    @string.byteslice(0, Primitive.match_data_byte_begin(@match, 0)) if @match
  end

  private def reset_state
    @prev_pos = @pos = 0
    @match = nil
  end

  def reset
    reset_state
    self
  end

  def rest
    @string.byteslice(@pos, @string.bytesize - @pos)
  end

  def rest?
    !eos?
  end

  def rest_size
    @string.bytesize - @pos
  end

  def restsize
    warn 'StringScanner#restsize is obsolete; use #rest_size instead' if $VERBOSE
    rest_size
  end

  def scan(pattern)
    scan_internal pattern, true, true, true
  end
  Primitive.always_split self, :scan

  def scan_byte
    if eos?
      @match = nil
      return nil
    end

    pos = @pos
    @match = Primitive.matchdata_create_single_group(/./mn, @string, pos, pos + 1)
    @prev_pos = pos
    @pos = pos + 1

    @string.getbyte(pos)
  end

  def scan_integer(base: 10)
    unless @string.encoding.ascii_compatible?
      raise Encoding::CompatibilityError, "ASCII incompatible encoding: #{@string.encoding.name}"
    end

    case base
    when 10
      substr = scan(/[+-]?\d+/)
    when 16
      substr = scan(/[+-]?(?:0x)?[0-9a-fA-F]+/)
    else
      raise ArgumentError, "Unsupported integer base: #{base.inspect}, expected 10 or 16"
    end

    if substr
      substr.to_i(base)
    end
  end

  def scan_until(pattern)
    scan_internal pattern, true, true, false
  end
  Primitive.always_split self, :scan_until

  def scan_full(pattern, advance_pos, getstr)
    scan_internal pattern, advance_pos, getstr, true
  end
  Primitive.always_split self, :scan_full

  def search_full(pattern, advance_pos, getstr)
    scan_internal pattern, advance_pos, getstr, false
  end
  Primitive.always_split self, :search_full

  def self.must_C_version
    self
  end

  def size
    @match&.size
  end

  def skip(pattern)
    scan_internal pattern, true, false, true
  end
  Primitive.always_split self, :skip

  def skip_until(pattern)
    scan_internal pattern, true, false, false
  end
  Primitive.always_split self, :skip_until

  def string
    @original
  end

  def string=(string)
    reset_state

    if string.instance_of? String
      @original = string
      @string = string
    else
      @original = StringValue(string)
      @string = String.new @original
    end
  end

  def terminate
    @match = nil
    @pos = string.bytesize
    self
  end

  def unscan
    raise Error unless @match
    @pos = @prev_pos
    @prev_pos = nil
    @match = nil
    self
  end

  def values_at(*args)
    @match&.values_at(*args)
  end

  def peek(len)
    raise ArgumentError if len < 0
    return '' if len.zero?
    @string.byteslice(@pos, len)
  end

  def peek_byte
    @string.getbyte(@pos)
  end

  def peep(len)
    warn 'StringScanner#peep is obsolete; use #peek instead' if $VERBOSE
    peek len
  end

  private def scan_check_args(pattern)
    unless Primitive.is_a?(pattern, Regexp) || Primitive.is_a?(pattern, String)
      raise TypeError, "bad pattern argument: #{pattern.inspect}"
    end

    raise ArgumentError, 'uninitialized StringScanner object' unless @string
  end

  # This method is kept very small so that it should fit within 100
  # AST nodes and can be split. This is done to avoid indirect calls
  # to TRegex.
  private def scan_internal(pattern, advance_pos, getstr, only_match_at_start)
    scan_check_args(pattern)

    if Primitive.is_a?(pattern, String)
      md = scan_internal_string_pattern(pattern, only_match_at_start)
    else
      start = @fixed_anchor ? 0 : @pos
      if only_match_at_start
        md = Primitive.regexp_match_at_start(pattern, @string, @pos, start)
      else
        md = Primitive.regexp_search_with_start(pattern, @string, @pos, start)
      end
    end

    if md
      @match = md
      scan_internal_set_pos_and_str(advance_pos, getstr, md)
    else
      @match = nil
    end
  end
  Primitive.always_split self, :scan_internal

  private def scan_internal_string_pattern(pattern, only_match_at_start)
    pos = @pos

    if only_match_at_start
      if @string.byteslice(pos..).start_with?(pattern)
        Primitive.matchdata_create_single_group(pattern, @string.dup, pos, pos + pattern.bytesize)
      else
        nil
      end
    else
      relative_pos = @string.byteslice(pos..).byteindex(pattern)
      if relative_pos
        found_pos = pos + relative_pos
        Primitive.matchdata_create_single_group(pattern, @string.dup, found_pos, found_pos + pattern.bytesize)
      else
        nil
      end
    end
  end

  private def scan_internal_set_pos_and_str(advance_pos, getstr, md)
    fin = Primitive.match_data_byte_end(md, 0)

    @prev_pos = @pos
    @pos = fin if advance_pos

    width = fin - @prev_pos
    return width unless getstr

    @string.byteslice(@prev_pos, width)
  end

end

unless defined?(::ScanError)
  class Object
    ScanError = StringScanner::Error
    deprecate_constant :ScanError
  end
end
