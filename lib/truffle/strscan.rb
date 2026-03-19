# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026, Benoit Daloze
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

class StringScanner
  class Error < StandardError
  end
  ::Object::ScanError = Error
  ::Object.deprecate_constant :ScanError

  Version = '3.1.8'
  Id = '$Id$'

  def self.must_C_version = self

  attr_reader :string, :pos
  alias_method :pointer, :pos

  def initialize(string, options = nil, fixed_anchor: false)
    @string = Primitive.convert_with_to_str(string)
    @fixed_anchor = Primitive.as_boolean(fixed_anchor)
    @pos = 0
    @last_match = nil
  end

  def inspect
    return "#<#{Primitive.class(self)} (uninitialized)>" unless @string
    return "#<#{Primitive.class(self)} fin>" if eos?

    before =
      if @pos == 0
        ''
      elsif @pos < 5
        "#{@string.byteslice(0, @pos).inspect} "
      else
        "#{('...' + @string.byteslice(@pos - 5, 5)).inspect} "
      end

    after =
      if @pos >= @string.bytesize - 5
        " #{@string.byteslice(@pos..).inspect}"
      else
        " #{(@string.byteslice(@pos, 5) + '...').inspect}"
      end

    "#<#{Primitive.class(self)} #{@pos}/#{@string.bytesize} #{before}@#{after}>"
  end

  def pos=(new_pos)
    if new_pos < 0
      new_pos += @string.bytesize
    end
    raise RangeError, 'index out of range' if new_pos < 0
    raise RangeError, 'index out of range' if new_pos > @string.bytesize
    @pos = new_pos
  end
  alias_method :pointer=, :pos=

  def charpos = Primitive.string_byte_index_to_character_index(@string, @pos)

  def rest = @string.byteslice(@pos..)

  def rest_size = rest.size

  def concat(more_string)
    @string.concat(Primitive.convert_with_to_str(more_string))
    self
  end
  alias_method :<<, :concat

  def string=(other_string)
    @string = Primitive.convert_with_to_str(other_string)
    @pos = 0
    @last_match = nil
    other_string
  end

  def reset
    @pos = 0
    @last_match = nil
    self
  end

  def terminate
    @pos = @string.bytesize
    @last_match = nil
    self
  end

  def unscan
    if @last_match
      @pos = Primitive.match_data_byte_begin(@last_match, 0)
      @last_match = nil
      self
    else
      raise Error, 'unscan failed: previous match record not exist'
    end
  end

  # Predicates

  def fixed_anchor? = @fixed_anchor

  def beginning_of_line?
    @pos == 0 or @string.byteslice(@pos-1, 1) == "\n"
  end
  alias_method :bol?, :beginning_of_line?

  def eos?
    raise ArgumentError, 'uninitialized StringScanner object' unless @string
    @pos >= @string.bytesize
  end

  def rest?
    !eos?
  end

  # MatchData-like methods

  def matched? = !Primitive.nil?(@last_match)

  def matched = @last_match&.to_s

  def [](group)
    raise TypeError, 'no implicit conversion of Range into Integer' if Primitive.is_a?(group, Range)

    if @last_match
      @last_match[group]
    else
      nil
    end
  end

  def values_at(*groups) = @last_match&.values_at(*groups)

  def captures = @last_match&.captures

  def size = @last_match&.size

  def pre_match = @last_match&.pre_match

  def post_match = @last_match&.post_match

  def named_captures = @last_match&.named_captures || {}

  def matched_size
    if @last_match
      Primitive.match_data_byte_end(@last_match, 0) - Primitive.match_data_byte_begin(@last_match, 0)
    end
  end

  # Scan-like methods

  def peek(length)
    raise ArgumentError, 'negative string size (or size too big)' if length < 0
    @string.byteslice(@pos, length)
  end

  def peek_byte = @string.getbyte(@pos)

  def get_byte
    return nil if eos?

    byte = @string.byteslice(@pos, 1)
    to = @pos + 1
    @last_match = Primitive.matchdata_create_single_group(byte, @string, @pos, to)
    @pos = to
    byte
  end

  def scan_byte
    if rest?
      byte_value = @string.getbyte(@pos)
      get_byte
      byte_value
    end
  end

  def getch = scan(/./)

  def scan_full(pattern, advance_pointer, return_string)
    if advance_pointer
      if return_string
        scan(pattern)
      else
        skip(pattern)
      end
    else
      if return_string
        check(pattern)
      else
        match?(pattern)
      end
    end
  end
  Primitive.always_split self, :scan_full

  def search_full(pattern, advance_pointer, return_string)
    if advance_pointer
      if return_string
        scan_until(pattern)
      else
        skip_until(pattern)
      end
    else
      if return_string
        check_until(pattern)
      else
        exist?(pattern)
      end
    end
  end
  Primitive.always_split self, :search_full

  # Keep the following 8 methods in sync, they are small variations of one another

  # Matches at start methods

  # Matches at start, returns matched string, does not advance position
  def check(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_match_at_start(pattern, @string, prev, start)
      if @last_match
        @last_match.to_s
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if rest.start_with?(pattern)
        to = prev + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, prev, to)
        pattern
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :check

  # Matches at start, returns matched string, advances position
  def scan(pattern)
    raise ArgumentError, 'uninitialized StringScanner object' unless @string
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_match_at_start(pattern, @string, prev, start)
      if @last_match
        @pos = Primitive.match_data_byte_end(@last_match, 0)
        @last_match.to_s
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if rest.start_with?(pattern)
        to = prev + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, prev, to)
        @pos = to
        pattern
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :scan

  # Matches at start, returns matched bytesize, does not advance position
  def match?(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_match_at_start(pattern, @string, prev, start)
      if @last_match
        to = Primitive.match_data_byte_end(@last_match, 0)
        to - prev
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if rest.start_with?(pattern)
        to = prev + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, prev, to)
        to - prev
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :match?

  # Matches at start, returns matched bytesize, advances position
  def skip(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_match_at_start(pattern, @string, prev, start)
      if @last_match
        to = Primitive.match_data_byte_end(@last_match, 0)
        @pos = to
        to - prev
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if rest.start_with?(pattern)
        to = prev + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, prev, to)
        @pos = to
        to - prev
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :skip

  # Matches anywhere methods

  # Matches anywhere, returns matched string, does not advance position
  def check_until(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_search_with_start(pattern, @string, prev, start)
      if @last_match
        to = Primitive.match_data_byte_end(@last_match, 0)
        @string.byteslice(prev, to - prev)
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if from = @string.byteindex(pattern, prev)
        to = from + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, from, to)
        @string.byteslice(prev, to - prev)
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :check_until

  # Matches anywhere, returns matched string, advances position
  def scan_until(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_search_with_start(pattern, @string, prev, start)
      if @last_match
        to = Primitive.match_data_byte_end(@last_match, 0)
        @pos = to
        @string.byteslice(prev, to - prev)
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if from = @string.byteindex(pattern, prev)
        to = from + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, from, to)
        @pos = to
        @string.byteslice(prev, to - prev)
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :scan_until

  # Matches anywhere, returns matched bytesize, does not advance position
  def exist?(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_search_with_start(pattern, @string, prev, start)
      if @last_match
        to = Primitive.match_data_byte_end(@last_match, 0)
        to - prev
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if from = @string.byteindex(pattern, prev)
        to = from + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, from, to)
        to - prev
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :exist?

  # Matches anywhere, returns matched bytesize, advances position
  def skip_until(pattern)
    prev = @pos
    if Primitive.is_a?(pattern, Regexp)
      start = @fixed_anchor ? 0 : prev
      @last_match = Primitive.regexp_search_with_start(pattern, @string, prev, start)
      if @last_match
        to = Primitive.match_data_byte_end(@last_match, 0)
        @pos = to
        to - prev
      end
    else
      pattern = Primitive.convert_with_to_str(pattern)
      if from = @string.byteindex(pattern, prev)
        to = from + pattern.bytesize
        @last_match = Primitive.matchdata_create_single_group(pattern, @string, from, to)
        @pos = to
        to - prev
      else
        @last_match = nil
      end
    end
  end
  Primitive.always_split self, :skip_until

  # Private methods

  private def scan_base10_integer
    scan(/[+-]?\d+/)&.to_i
  end

  private def scan_base16_integer
    scan(/[+-]?(0x)?[0-9a-fA-F]+/)&.to_i(16)
  end
end
