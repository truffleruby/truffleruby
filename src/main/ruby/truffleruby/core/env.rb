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

  private def init
    vars = Truffle::System.initial_environment_variables
    @variables = vars.map { |name| set_encoding(name) }
  end

  def size
    @variables.size
  end
  alias_method :length, :size

  private def lookup(key)
    value = Truffle::POSIX.getenv(Primitive.convert_with_to_str(key))
    if value
      value = set_encoding(value)
    end
    value
  end

  def [](key)
    lookup(key)
  end

  def []=(key, value)
    key = Primitive.convert_with_to_str(key)
    if Primitive.nil? value
      Truffle::POSIX.unsetenv(key)
      @variables.delete(key)
    else
      if Truffle::POSIX.setenv(key, Primitive.convert_with_to_str(value), 1) != 0
        Errno.handle('setenv')
      end
      unless @variables.include?(key)
        @variables << set_encoding(key.dup)
      end
    end
    value
  end
  alias_method :store, :[]=

  def clone
    raise TypeError, 'Cannot clone ENV, use ENV.to_h to get a copy of ENV as a hash'
  end

  def delete(key)
    key = Primitive.convert_with_to_str(key)
    existing_value = lookup(key)
    if existing_value
      Truffle::POSIX.unsetenv(key)
      @variables.delete(key)
      existing_value
    elsif block_given?
      yield key
    end
  end

  def dup
    raise TypeError, 'Cannot dup ENV, use ENV.to_h to get a copy of ENV as a hash'
  end

  def shift
    key = @variables.first
    return nil unless key
    value = delete key

    key = set_encoding key
    value = set_encoding value

    [key, value]
  end

  def each
    return to_enum(:each) { size } unless block_given?

    @variables.each do |name|
      key = set_encoding(name)
      value = lookup(name)
      yield key, value
    end

    self
  end
  alias_method :each_pair, :each

  def each_key
    return to_enum(:each_key) { size } unless block_given?
    @variables.each do |name|
      yield set_encoding(name)
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

    # Avoid deleting from the environment while iterating.
    keys = []
    each { |k, v| keys << k if yield(k, v) }
    keys.each { |k| delete k }

    keys.empty? ? nil : self
  end

  def clear
    # Avoid deleting from the environment while iterating.
    keys = []
    each { |k, _v| keys << k }
    keys.each { |k| delete k }

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

  def index(value)
    warn 'ENV.index is deprecated; use ENV.key', uplevel: 1
    key(value)
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
    keys_to_delete = keys
    other.each do |k, v|
      self[k] = v
      keys_to_delete.delete(k)
    end
    keys_to_delete.each { |k| delete(k) }
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
        other.each { |k, v| self[k] = v }
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

Truffle::Boot.delay do
  ENV.send(:init)
end

# JRuby uses this for example to make proxy settings visible to stdlib/uri/common.rb

ENV_JAVA = {}
