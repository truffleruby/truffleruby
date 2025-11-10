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

class Hash
  include Enumerable

  def self.contains_all_internal(one, two)
    one.all? do |key, value|
      if two.has_key?(key)
        two_value = two[key]
        Primitive.equal?(value, two_value) || value == two_value
      else
        false
      end
    end
  end

  def self.new_from_associate_array(associate_array)
    hash = new
    associate_array.each_with_index do |array, i|
      unless array.respond_to? :to_ary
        warn "wrong element type #{Primitive.class(array)} at #{i} (expected array)"
        warn 'ignoring wrong elements is deprecated, remove them explicitly'
        warn 'this causes ArgumentError in the next release'
        next
      end
      array = array.to_ary
      unless (1..2).cover? array.size
        raise ArgumentError, "invalid number of elements (#{array.size} for 1..2)"
      end
      hash[array.at(0)] = array.at(1)
    end
    hash
  end
  private_class_method :new_from_associate_array

  def self.try_convert(obj)
    Truffle::Type.try_convert obj, Hash, :to_hash
  end

  # Fallback for Hash.[]
  def self._constructor_fallback(*args)
    if args.size == 1
      obj = args.first
      if hash = Truffle::Type.rb_check_convert_type(obj, Hash, :to_hash)
        new_hash = allocate.merge!(hash)
        new_hash.default = nil
        return new_hash
      elsif associate_array = Truffle::Type.rb_check_convert_type(obj, Array, :to_ary)
        return new_from_associate_array(associate_array)
      end
    end

    return new if args.empty?

    if args.size.odd?
      raise ArgumentError, "Expected an even number, got #{args.length}"
    end

    hash = new
    i = 0
    total = args.size

    while i < total
      hash[args[i]] = args[i+1]
      i += 2
    end

    hash
  end
  private_class_method :_constructor_fallback

  def self.ruby2_keywords_hash(hash)
    Primitive.hash_copy_and_mark_as_ruby2_keywords(hash)
  end

  alias_method :store, :[]=

  def <(other)
    other = Truffle::Type.coerce_to(other, Hash, :to_hash)
    return false if self.size >= other.size
    Primitive.class(self).contains_all_internal(self, other)
  end

  def <=(other)
    other = Truffle::Type.coerce_to(other, Hash, :to_hash)
    return false if self.size > other.size
    Primitive.class(self).contains_all_internal(self, other)
  end

  def ==(other)
    eql_op(:==, other)
  end

  def eql?(other)
    eql_op(:eql?, other)
  end

  def eql_op(op, other)
    return true if Primitive.equal?(self, other)
    unless Primitive.is_a?(other, Hash)
      return false unless other.respond_to? :to_hash
      return other.send(op, self)
    end

    return false unless other.size == size

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      each_pair do |key, value|
        other_value = Primitive.hash_get_or_undefined(other, key)

        # Other doesn't even have this key
        return false if Primitive.undefined?(other_value)

        # Order of the comparison matters! We must compare our value with
        # the other Hash's value and not the other way around.
        unless Primitive.equal?(value, other_value) or value.send(op, other_value)
          return false
        end
      end
    end
    true
  end
  private :eql_op

  def >(other)
    other = Truffle::Type.coerce_to(other, Hash, :to_hash)
    return false if self.size <= other.size
    Primitive.class(self).contains_all_internal(other, self)
  end

  def >=(other)
    other = Truffle::Type.coerce_to(other, Hash, :to_hash)
    return false if self.size < other.size
    Primitive.class(self).contains_all_internal(other, self)
  end

  def assoc(key)
    each_pair { |k,v| return k, v if key == k }
    nil
  end

  def compact
    rejected = reject { |_k, v| Primitive.nil? v }
    rejected.default = default if default
    rejected.default_proc = default_proc if default_proc
    rejected
  end

  def compact!
    reject! { |_k, v| Primitive.nil? v }
  end

  def default(key = undefined)
    if default_proc and !Primitive.undefined?(key)
      default_proc.call(self, key)
    else
      Primitive.hash_default_value self
    end
  end

  def default_proc=(proc)
    Primitive.check_frozen self
    unless Primitive.nil? proc
      proc = Truffle::Type.coerce_to proc, Proc, :to_proc

      if proc.lambda? and proc.arity != 2
        raise TypeError, 'default proc must have arity 2'
      end
    end

    Primitive.hash_set_default_proc self, proc
  end

  def dig(key, *more)
    result = self[key]
    if Primitive.nil?(result) || more.empty?
      result
    else
      Truffle::Diggable.dig(result, more)
    end
  end

  def deconstruct_keys(keys)
    self
  end

  def except(*keys)
    new_hash = {}.replace(dup)
    new_hash.default = nil
    keys.each do |k|
      new_hash.delete(k)
    end
    new_hash
  end

  def fetch(key, default = undefined)
    value = Primitive.hash_get_or_undefined(self, key)
    unless Primitive.undefined?(value)
      return value
    end

    if block_given?
      Primitive.warn_block_supersedes_default_value_argument unless Primitive.undefined?(default)

      return yield(key)
    end

    return default unless Primitive.undefined?(default)
    raise KeyError.new("key not found: #{key.inspect}", receiver: self, key: key)
  end

  def fetch_values(*keys, &block)
    keys.map do |key|
      self.fetch(key, &block)
    end
  end

  def flatten(level = 1)
    to_a.flatten(level)
  end

  def keep_if
    return to_enum(:keep_if) { size } unless block_given?

    Primitive.check_frozen self

    each_pair { |k,v| delete k unless yield(k, v) }

    self
  end

  def merge(*others)
    current = self.dup

    if block_given?
      others.each do |other|
        other.each do |k, v|
          other = Truffle::Type.coerce_to(other, Hash, :to_hash)
          if current.include?(k)
            current[k] = yield(k, current[k], v)
          else
            current[k] = v
          end
        end
      end
    else
      others.each do |other|
        other = Truffle::Type.coerce_to(other, Hash, :to_hash)
        current = Truffle::HashOperations.hash_merge(current, other)
      end
    end
    current
  end

  def merge!(*others)
    Primitive.check_frozen self

    others.each do |other|
      other = Truffle::Type.coerce_to other, Hash, :to_hash

      if block_given?
        other.each_pair do |key,value|
          if key? key
            Primitive.hash_store(self, key, yield(key, self[key], value))
          else
            Primitive.hash_store(self, key, value)
          end
        end
      else
        other.each_pair do |key,value|
          Primitive.hash_store(self, key, value)
        end
      end
    end
    self
  end
  alias_method :update, :merge!

  def rassoc(value)
    each_pair { |k,v| return k, v if value == v }
    nil
  end

  def select
    return to_enum(:select) { size } unless block_given?

    selected = Hash.allocate
    selected.compare_by_identity if compare_by_identity?

    each_pair do |key,value|
      if yield(key, value)
        selected[key] = value
      end
    end

    selected
  end

  alias_method :filter, :select

  def select!
    return to_enum(:select!) { size } unless block_given?

    Primitive.check_frozen self

    return nil if empty?

    previous_size = size
    each_pair { |k,v| delete k unless yield(k, v) }
    return nil if previous_size == size

    self
  end

  alias_method :filter!, :select!

  def slice(*keys)
    res = {}
    res.compare_by_identity if compare_by_identity?
    keys.each do |k|
      v = Primitive.hash_get_or_undefined(self, k)
      res[k] = v unless Primitive.undefined?(v)
    end
    res
  end

  def to_h
    if block_given?
      h = {}
      each do |k, v|
        pair = yield(k, v)
        Truffle::HashOperations.assoc_key_value_pair(h, pair)
      end
      h
    elsif instance_of? Hash
      self
    else
      Hash.allocate.replace(to_hash)
    end
  end

  # Random number for hash codes. Stops hashes for similar values in
  # different classes from clashing, but defined as a constant so
  # that hashes will be deterministic.

  CLASS_SALT = 0xC5F7D8

  private_constant :CLASS_SALT

  def hash
    val = Primitive.vm_hash_start CLASS_SALT
    val = Primitive.vm_hash_update val, size
    Truffle::ThreadOperations.detect_outermost_recursion self do
      each_pair do |key,value|
        entry_val = Primitive.vm_hash_start key.hash
        entry_val = Primitive.vm_hash_update entry_val, value.hash
        # We have to combine these with xor as the hash must not depend on hash order.
        val ^= Primitive.vm_hash_end entry_val
      end
    end

    Primitive.vm_hash_end val
  end

  def delete_if(&block)
    return to_enum(:delete_if) { size } unless block_given?

    Primitive.check_frozen self

    select(&block).each { |k, _v| delete k }
    self
  end

  def each_key
    return to_enum(:each_key) { size } unless block_given?

    each_pair { |key,_value| yield key }
    self
  end

  def each_value
    return to_enum(:each_value) { size } unless block_given?

    each_pair { |_key,value| yield value }
    self
  end

  def key(value)
    each_pair do |k,v|
      return k if v == value
    end
    nil
  end

  def inspect
    out = []
    return +'{...}' if Truffle::ThreadOperations.detect_recursion self do
      each_pair do |key,value|
        out << "#{Truffle::Type.rb_inspect(key)}=>#{Truffle::Type.rb_inspect(value)}"
      end
    end

    "{#{out.join ', '}}"
  end
  alias_method :to_s, :inspect

  def key?(key)
    !Primitive.undefined?(Primitive.hash_get_or_undefined(self, key))
  end
  alias_method :has_key?, :key?
  alias_method :include?, :key?
  alias_method :member?, :key?

  def keys
    ary = []
    each_key do |key|
      ary << key
    end
    ary
  end

  def reject(&block)
    return to_enum(:reject) { size } unless block_given?
    copy = dup
    copy.default = nil
    copy.default_proc = nil
    copy.delete_if(&block)
    copy
  end

  def reject!(&block)
    return to_enum(:reject!) { size } unless block_given?

    Primitive.check_frozen self

    unless empty?
      previous_size = size
      delete_if(&block)
      return self if previous_size != size
    end

    nil
  end

  def sort(&block)
    to_a.sort(&block)
  end

  def to_a
    ary = []

    each_pair do |key,value|
      ary << [key, value]
    end

    ary
  end

  def value?(value)
    each_value do |v|
      return true if v == value
    end
    false
  end
  alias_method :has_value?, :value?

  def values
    ary = []

    each_value do |value|
      ary << value
    end

    ary
  end

  def values_at(*args)
    args.map { |key| self[key] }
  end

  def invert
    inverted = {}
    each_pair do |key, value|
      inverted[value] = key
    end
    inverted
  end

  def to_hash
    self
  end

  def to_proc
    proc_hash = self
    Proc.new do |*args|
      Truffle::Type.check_arity(args.size, 1, 1)
      proc_hash[args[0]]
    end
  end

  def transform_values
    return to_enum(:transform_values) { size } unless block_given?

    h = {}
    h.compare_by_identity if compare_by_identity?
    each_pair do |key, value|
      h[key] = yield(value)
    end
    h
  end

  def transform_values!
    return to_enum(:transform_values!) { size } unless block_given?

    Primitive.check_frozen self

    each_pair do |key, value|
      self[key] = yield(value)
    end
    self
  end

  def transform_keys(mapping = nil)
    has_block = block_given?
    h = {}

    if mapping
      mapping = Truffle::Type.coerce_to(mapping, Hash, :to_hash)
      each_pair do |key, value|
        k = Primitive.hash_get_or_undefined(mapping, key)
        k = has_block ? yield(key) : key if Primitive.undefined?(k)
        h[k] = value
      end
    else
      return to_enum(:transform_keys) { size } unless has_block

      each_pair do |key, value|
        h[yield(key)] = value
      end
    end

    h
  end

  def transform_keys!(mapping = nil)
    has_block = block_given?
    return to_enum(:transform_keys!) { size } unless mapping || has_block

    Primitive.check_frozen self
    h = {}

    begin
      if mapping
        mapping = Truffle::Type.coerce_to(mapping, Hash, :to_hash)
        each_pair do |key, value|
          k = Primitive.hash_get_or_undefined(mapping, key)
          k = has_block ? yield(key) : key if Primitive.undefined?(k)
          h[k] = value
        end
      else
        each_pair do |key, value|
          h[yield(key)] = value
        end
      end
    ensure
      replace h
    end
    self
  end
end
