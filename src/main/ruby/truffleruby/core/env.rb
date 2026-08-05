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

ENV = Object.new

class << ENV
  include Enumerable

  def size
    environ_keys.size
  end
  alias_method :length, :size

  private def lookup(key)
    key = Primitive.convert_with_to_str(key)
    value = Truffle::POSIX.getenv(key)
    value && set_encoding(value)
  end

  private def environ_pointer
    # The environ_pointer never changes, so it's safe to cache.
    # Every thread would see the same value, so data races aren't a concern.
    @environ_pointer ||= Truffle::POSIX.truffleposix_environ_address
  end

  # Walks environ and yields each "NAME=VALUE" entry along with the index of
  # its '=', so that callers can take just the name or both parts.
  private def each_environ_entry
    array = environ_pointer.read_pointer

    index = 0
    until (entry = array.get_pointer(index * Truffle::FFI::Pointer::SIZE)).null?
      string = entry.read_string_to_null
      separator = string.index('=')

      # An entry without a '=' is not a variable. Skip it like MRI's `env_each_pair` does.
      yield string, separator if separator
      index += 1
    end
  end

  private def environ_entries
    entries = []
    each_environ_entry do |string, separator|
      entries << [environ_string(string[0...separator]), environ_string(string[(separator + 1)..-1])]
    end
    entries
  end

  # The equivalent of MRI's `env_keys`, for callers that do not need the values.
  private def environ_keys
    keys = []
    each_environ_entry do |string, separator|
      keys << environ_string(string[0...separator])
    end
    keys
  end

  def [](key)
    lookup(key)
  end

  def []=(key, value)
    key = Primitive.convert_with_to_str(key)
    env_set(key, value)
    value
  end
  alias_method :store, :[]=

  private def env_set(key, value)
    if Primitive.nil? value
      Truffle::POSIX.unsetenv(key)
    else
      if Truffle::POSIX.setenv(key, Primitive.convert_with_to_str(value), 1) != 0
        Errno.handle('setenv')
      end
    end
  end

  def clone
    raise TypeError, 'Cannot clone ENV, use ENV.to_h to get a copy of ENV as a hash'
  end

  def delete(key)
    key = Primitive.convert_with_to_str(key)
    existing_value = Truffle::POSIX.getenv(key)
    Truffle::POSIX.unsetenv(key) if existing_value

    if existing_value
      set_encoding(existing_value)
    elsif block_given?
      yield key
    end
  end

  def dup
    raise TypeError, 'Cannot dup ENV, use ENV.to_h to get a copy of ENV as a hash'
  end

  def shift
    key = environ_keys.first
    return nil unless key

    value = Truffle::POSIX.getenv(key)
    Truffle::POSIX.unsetenv(key)

    [set_encoding(key), set_encoding(value)]
  end

  def each
    return to_enum(:each) { size } unless block_given?

    environ_entries.each do |key, value|
      yield set_encoding(key), set_encoding(value)
    end

    self
  end
  alias_method :each_pair, :each

  def each_key
    return to_enum(:each_key) { size } unless block_given?
    environ_keys.each do |key|
      yield set_encoding(key)
    end
    self
  end

  def each_value
    return to_enum(:each_value) { size } unless block_given?

    each { |_k, v| yield v }
  end

  def delete_if(&block)
    return to_enum(:delete_if) { size } unless block_given?
    reject!(&block)
    self
  end

  # More efficient than using the one from Enumerable
  def include?(key)
    !Primitive.nil?(lookup(key))
  end
  alias_method :has_key?, :include?
  alias_method :key?, :include?
  alias_method :member?, :include?

  def fetch(key, absent = undefined)
    if block_given? and !Primitive.undefined?(absent)
      Primitive.warn_block_supersedes_default_value_argument
    end

    if value = lookup(key)
      return value
    end

    if block_given?
      return yield(key)
    elsif Primitive.undefined?(absent)
      raise KeyError.new("key not found: #{key.inspect}", receiver: self, key: key)
    end

    absent
  end

  def to_s
    'ENV'
  end

  def inspect
    to_hash.inspect
  end

  def reject(&block)
    to_hash.reject(&block)
  end

  def reject!
    return to_enum(:reject!) { size } unless block_given?

    # Collect all the keys before deleting any env vars, since the block may modify the environment.
    keys = []
    each { |k, v| keys << k if yield(k, v) }

    keys.each do |key|
      Truffle::POSIX.unsetenv(key)
    end

    keys.empty? ? nil : self
  end

  def clear
    environ_keys.each do |key|
      Truffle::POSIX.unsetenv(key)
    end

    self
  end

  def has_value?(value)
    value = Truffle::Type.rb_check_convert_type(value, String, :to_str)
    return nil if Primitive.nil? value
    each { |_k, v| return true if v == value }
    false
  end
  alias_method :value?, :has_value?

  def values_at(*params)
    params.map { |k| lookup(k) }
  end

  def invert
    to_hash.invert
  end

  def key(value)
    value = Primitive.convert_with_to_str(value);
    each do |k, v|
      return k if v == value
    end
    nil
  end

  def keys
    keys = []
    each { |k, _v| keys << k }
    keys
  end

  def values
    vals = []
    each { |_k, v| vals << v }
    vals
  end

  def empty?
    each { return false }
    true
  end

  def rehash
    # No need to do anything, our keys are always strings
  end

  def replace(other)
    return self if Primitive.equal?(self, other)
    other = Primitive.convert_with_to_hash(other)

    keys_to_delete = environ_keys.map(&:b)

    other.each do |k, v|
      key = Primitive.convert_with_to_str(k)
      env_set(key, v)
      keys_to_delete.delete(key.b)
    end

    keys_to_delete.each do |key|
      Truffle::POSIX.unsetenv(key)
    end

    self
  end

  def select(&blk)
    return to_enum(:select) { size } unless block_given?
    to_hash.select(&blk)
  end
  alias_method :filter, :select

  def to_a
    ary = []
    each { |k, v| ary << [k, v] }
    ary
  end

  def to_hash
    h = {}
    each_pair do |key, value|
      h[key] = value
    end
    h
  end

  def to_h
    return to_hash unless block_given?

    h = {}
    each_pair do |k, v|
      pair = yield(k, v)
      Truffle::HashOperations.assoc_key_value_pair(h, pair)
    end
    h
  end

  def update(*others)
    others.each do |other|
      next if Primitive.equal?(self, other)

      other = Primitive.convert_with_to_hash(other)

      if block_given?
        other.each do |k, v|
          if include?(k)
            self[k] = yield(k, lookup(k), v)
          else
            self[k] = v
          end
        end
      else
        other.each do |k, v|
          env_set(Primitive.convert_with_to_str(k), v)
        end
      end
    end

    self
  end
  alias_method :merge!, :update

  def keep_if(&block)
    return to_enum(:keep_if) { size } unless block_given?
    select!(&block)
    self
  end

  def select!
    return to_enum(:select!) { size } unless block_given?
    reject! { |k, v| !yield(k, v) }
  end
  alias_method :filter!, :select!

  def assoc(key)
    key = Primitive.convert_with_to_str(key)
    value = lookup(key)
    value ? [key, value] : nil
  end

  def rassoc(value)
    value = Truffle::Type.rb_check_convert_type(value, String, :to_str)
    return nil if Primitive.nil? value
    key = key(value)
    key ? [key, value] : nil
  end

  def slice(*keys)
    result = {}
    keys.each do |k|
      value = lookup(k)
      unless Primitive.nil? value
        result[k] = value
      end
    end
    result
  end

  def except(*keys)
    # More memory-efficient than delegating to Hash.except
    result = to_hash
    keys.each { |k| result.delete(k) }

    result
  end

  # Equivalent of MRI's `env_str_new`.
  private def environ_string(string)
    if Encoding::LOCALE == Encoding::US_ASCII && !string.ascii_only?
      string.force_encoding(Encoding::BINARY)
    else
      string.force_encoding(Encoding::LOCALE)
    end
  end

  def set_encoding(value)
    return unless Primitive.is_a?(value, String)
    if Encoding.default_internal && value.ascii_only?
      value = value.encode Encoding.default_internal, Encoding::LOCALE
    elsif value.encoding != Encoding::LOCALE
      if Encoding::LOCALE == Encoding::US_ASCII && !value.ascii_only?
        value = value.b
      else
        value = value.dup.force_encoding(Encoding::LOCALE)
      end
    end
    value.freeze
  end
  private :set_encoding
end

# JRuby uses this for example to make proxy settings visible to stdlib/uri/common.rb

ENV_JAVA = {}
