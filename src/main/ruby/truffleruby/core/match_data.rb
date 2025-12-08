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

class MatchData
  class << self
    # Prevent allocating MatchData, like MRI 2.7+, so we don't need to check if it's initialized
    undef_method :allocate
  end

  def byteoffset(idx)
    backref = backref_from_arg(idx)
    [Primitive.match_data_byte_begin(self, backref), Primitive.match_data_byte_end(self, backref)]
  end

  def bytebegin(idx)
    backref = backref_from_arg(idx)
    Primitive.match_data_byte_begin(self, backref)
  end

  def byteend(idx)
    backref = backref_from_arg(idx)
    Primitive.match_data_byte_end(self, backref)
  end

  def offset(idx)
    [self.begin(idx), self.end(idx)]
  end

  def ==(other)
    return true if Primitive.equal?(self, other)

    Primitive.is_a?(other, MatchData) &&
      string == other.string  &&
      regexp == other.regexp  &&
      captures == other.captures
  end
  alias_method :eql?, :==

  def string
    Primitive.match_data_get_source(self).dup.freeze
  end

  def captures
    to_a[1..-1]
  end
  alias_method :deconstruct, :captures

  def deconstruct_keys(array_of_names)
    Truffle::Type.rb_check_type(array_of_names, Array) unless Primitive.nil?(array_of_names)

    hash = named_captures.transform_keys(&:to_sym)
    return hash if Primitive.nil?(array_of_names)

    ret = {}
    return ret if array_of_names.size > hash.size

    array_of_names.each do |key|
      Truffle::Type.rb_check_type(key, Symbol)
      value = Primitive.hash_get_or_undefined(hash, key)
      break if Primitive.undefined?(value)
      ret[key] = value
    end

    ret
  end

  def names
    regexp.names
  end

  def named_captures(symbolize_names: false)
    names = Primitive.regexp_names(self.regexp).map(&:first)
    names.map!(&:to_s) unless symbolize_names
    names.collect { |name| [name, self[name]] }.to_h
  end

  def begin(index)
    backref = backref_from_arg(index)
    Primitive.match_data_begin(self, backref)
  end

  def end(index)
    backref = backref_from_arg(index)
    Primitive.match_data_end(self, backref)
  end

  def inspect
    str = "#<MatchData \"#{self[0]}\""
    idx = 0
    captures.zip(names) do |capture, name|
      idx += 1
      str << " #{name || idx}:#{capture.inspect}"
    end
    "#{str}>"
  end

  def values_at(*indexes)
    out = []
    size = self.size

    indexes.each do |elem|
      if Primitive.is_a?(elem, String) || Primitive.is_a?(elem, Symbol)
        out << self[elem]
      elsif Primitive.is_a?(elem, Range)
        start, length = Primitive.range_normalized_start_length(elem, size)
        finish = start + length - 1

        raise RangeError, "#{elem} out of range" if start < 0
        next if finish < start # ignore empty ranges

        finish_in_bounds = [finish, size - 1].min
        start.upto(finish_in_bounds) do |index|
          out << self[index]
        end

        (finish_in_bounds + 1).upto(finish) { out << nil }
      else
        index = Primitive.rb_num2int(elem)
        if index >= size || index < -size
          out << nil
        else
          out << self[index]
        end
      end
    end

    out
  end

  def match(n)
    # Similar, but #match accepts only single index/name, but not a range or an optional length.
    number = Truffle::Type.rb_check_convert_type(n, Integer, :to_int)
    return self[number] if number
    # To convert the last type (String) we used rb_convert_type instead of rb_check_convert_type which throws an exception
    name = Truffle::Type.rb_check_convert_type(n, Symbol, :to_sym) || Truffle::Type.rb_convert_type(n, String, :to_str)
    self[name]
  end

  def match_length(n)
    match(n)&.length
  end

  def to_s
    self[0]
  end

  private

  def backref_from_arg(index)
    if Primitive.is_a?(index, String) || Primitive.is_a?(index, Symbol)
      names_to_backref = Hash[Primitive.regexp_names(self.regexp)]
      array = names_to_backref[index.to_sym]

      raise IndexError, "undefined group name reference: #{index}" unless array

      return array.last
    end

    Primitive.rb_to_int(index)
  end
end

Truffle::KernelOperations.define_hooked_variable(
  :$~,
  -> s { Primitive.regexp_last_match_get(s) },
  Truffle::RegexpOperations::LAST_MATCH_SET)

# Prism gives a SyntaxError if $` is not aliased
Truffle::KernelOperations.define_hooked_variable(
  :'$`',
  -> s { match = Primitive.regexp_last_match_get(s)
         match.pre_match if match },
  -> name, _ { raise NameError, "#{name} is a read-only variable" },
  -> s { 'global-variable' if Primitive.regexp_last_match_get(s) })

# Prism gives a SyntaxError if $' is not aliased
Truffle::KernelOperations.define_hooked_variable(
  :"$'",
  -> s { match = Primitive.regexp_last_match_get(s)
         match.post_match if match },
  -> name, _ { raise NameError, "#{name} is a read-only variable" },
  -> s { 'global-variable' if Primitive.regexp_last_match_get(s) })

# Prism gives a SyntaxError if $& is not aliased
Truffle::KernelOperations.define_hooked_variable(
  :'$&',
  -> s { match = Primitive.regexp_last_match_get(s)
         match[0] if match },
  -> name, _ { raise NameError, "#{name} is a read-only variable" },
  -> s { 'global-variable' if Primitive.regexp_last_match_get(s) })

# Prism gives a SyntaxError if $+ is not aliased
Truffle::KernelOperations.define_hooked_variable(
  :'$+',
  -> s { match = Primitive.regexp_last_match_get(s)
         match.captures.reverse.find { |m| !Primitive.nil?(m) } if match },
  -> name, _ { raise NameError, "#{name} is a read-only variable" },
  -> s { 'global-variable' if Primitive.regexp_last_match_get(s) })
