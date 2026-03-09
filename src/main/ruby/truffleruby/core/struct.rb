# frozen_string_literal: true

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

class Struct
  include Enumerable

  class << self
    alias_method :subclass_new, :new
  end

  # Process:Tms (tms.rb) is duplicating some of this logic and must be kept in sync.

  def self.new(*attrs, keyword_init: nil, &block)
    klass_name = nil

    first = attrs[0]
    if attrs.size >= 1 && !Primitive.is_a?(first, Symbol)
      # nil check because Struct.new(nil, :foo) is valid
      klass_name = Primitive.convert_to_str(first) unless Primitive.nil?(first)
      attrs.shift
    end

    attrs = attrs.map { |a| Truffle::Type.symbol_or_string_to_symbol(a) }

    duplicates = attrs.uniq!
    if duplicates
      raise ArgumentError, "duplicate member: #{duplicates.first}"
    end

    klass = Class.new self do
      # _specialize doesn't support keyword arguments
      _specialize attrs if Primitive.false?(keyword_init)

      attrs.each do |a|
        define_method(a) { Primitive.object_hidden_var_get(self, a) }
        define_method(:"#{a}=") do |value|
          Primitive.check_frozen self
          Primitive.object_hidden_var_set(self, a, value)
        end
      end

      def self.new(...)
        subclass_new(...)
      end

      def self.[](*args)
        new(*args)
      end

      # This doesn't apply when keyword_init is nil.
      if keyword_init
        def self.inspect
          super + '(keyword_init: true)'
        end
      end

      const_set :STRUCT_ATTRS, attrs
      const_set :KEYWORD_INIT, keyword_init

      def self.keyword_init?
        return nil if Primitive.nil?(self::KEYWORD_INIT)
        Primitive.as_boolean(self::KEYWORD_INIT)
      end

      def self.members
        self::STRUCT_ATTRS.dup
      end
    end

    const_set klass_name, klass if klass_name

    klass.module_eval(&block) if block

    klass
  end

  def self.make_struct(name, attrs)
    new name, *attrs
  end

  private def _attrs # :nodoc:
    Primitive.class(self)::STRUCT_ATTRS
  end

  def select
    return to_enum(:select) { size } unless block_given?

    to_a.select do |v|
      yield v
    end
  end
  alias_method :filter, :select

  def to_h
    h = {}
    each_pair do |k, v|
      pair = block_given? ? yield(k, v) : [k, v]
      Truffle::HashOperations.assoc_key_value_pair(h, pair)
    end
    h
  end

  def to_s
    Truffle::ThreadOperations.detect_recursion(self) do
      values = []

      _attrs.each do |var|
        val = Primitive.object_hidden_var_get(self, var)
        values << "#{var}=#{val.inspect}"
      end

      if Primitive.module_anonymous?(Primitive.class(self))
        return "#<struct #{values.join(', ')}>"
      else
        name = Primitive.module_name Primitive.class(self)
        return "#<struct #{name} #{values.join(', ')}>"
      end
    end

    +'[...]'
  end
  alias_method :inspect, :to_s

  def initialize(*args, **kwargs)
    attrs = _attrs

    unless args.length <= attrs.length
      raise ArgumentError, "Expected #{attrs.size}, got #{args.size}"
    end

    keyword_init = Primitive.class(self)::KEYWORD_INIT

    # When keyword_init is nil:
    #   If there are any positional args we treat them all as positional.
    #   If there are no args at all we also want to run the positional handling code.

    if keyword_init || (Primitive.nil?(keyword_init) && args.empty? && !kwargs.empty?)
      # Accept a single positional hash for https://bugs.ruby-lang.org/issues/18632 and spec
      if kwargs.empty? && args.size == 1 && Primitive.is_a?(args.first, Hash)
        kwargs = args.first
      elsif args.size > 0
        raise ArgumentError, "wrong number of arguments (given #{args.size}, expected 0)"
      end

      unknowns = []
      kwargs.each_pair do |attr, value|
        if attrs.include?(attr)
          Primitive.object_hidden_var_set self, attr, value
        else
          unknowns << attr
        end
      end

      unless unknowns.empty?
        raise ArgumentError, "unknown keywords: #{unknowns.join(', ')}"
      end
    else
      attrs.each_with_index do |attr, i|
        Primitive.object_hidden_var_set self, attr, args[i]
      end

      # treat keyword arguments as a positional one
      if !kwargs.empty?
        if args.size < attrs.size
          Primitive.object_hidden_var_set self, attrs[args.size], kwargs
        else
          raise ArgumentError, 'struct size differs'
        end
      end
    end
  end

  def initialize_copy(other)
    other.__send__(:_attrs).each do |a|
      Primitive.object_hidden_var_set self, a, Primitive.object_hidden_var_get(other, a)
    end
    self
  end

  def ==(other)
    return false if Primitive.class(self) != Primitive.class(other)

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      return self.values == other.values
    end

    # Subtle: if we are here, we are recursing and haven't found any difference, so:
    true
  end

  private def read_or_nil(var)
    case var
    when Symbol, String
      var = var.to_sym
      return nil unless _attrs.include?(var)
    else
      var = Integer(var)
      a_len = _attrs.length
      return nil if var >= a_len or var < -a_len
      var = _attrs[var]
    end

    Primitive.object_hidden_var_get(self, var)
  end

  def [](var)
    case var
    when Symbol
      # ok
    when String
      var = var.to_sym
    else
      var = check_index_var!(var)
    end

    unless _attrs.include? var.to_sym
      raise NameError, "no member '#{var}' in struct"
    end

    Primitive.object_hidden_var_get(self, var)
  end

  def []=(var, obj)
    case var
    when Symbol
      unless _attrs.include? var
        raise NameError, "no member '#{var}' in struct"
      end
    when String
      var = var.to_sym
      unless _attrs.include? var
        raise NameError, "no member '#{var}' in struct"
      end
    else
      var = check_index_var!(var)
    end

    Primitive.check_frozen self
    Primitive.object_hidden_var_set(self, var, obj)
  end

  private def check_index_var!(var)
    var = Primitive.convert_type(var, Integer, :to_int)
    a_len = _attrs.length
    if var >= a_len
      raise IndexError, "offset #{var} too large for struct(size:#{a_len})"
    elsif var < -a_len
      raise IndexError, "offset #{var} too small for struct(size:#{a_len})"
    end
    _attrs[var]
  end

  def dig(key, *more)
    result = read_or_nil(key)
    if Primitive.nil?(result) || more.empty?
      result
    else
      Truffle::Diggable.dig(result, more)
    end
  end

  def eql?(other)
    return true if Primitive.equal?(self, other)
    return false if Primitive.class(self) != Primitive.class(other)

    Truffle::ThreadOperations.detect_pair_recursion self, other do
      _attrs.each do |var|
        mine =   Primitive.object_hidden_var_get(self, var)
        theirs = Primitive.object_hidden_var_get(other, var)

        return false unless mine.eql? theirs
      end
    end

    # Subtle: if we are here, then no difference was found, or we are recursing
    # In either case, return
    true
  end

  def each
    return to_enum(:each) { size } unless block_given?
    values.each do |v|
      yield v
    end
    self
  end

  def each_pair
    return to_enum(:each_pair) { size } unless block_given?
    _attrs.each { |var| yield [var, Primitive.object_hidden_var_get(self, var)] }
    self
  end

  def hash
    val = Primitive.vm_hash_start(Primitive.class(self).hash)
    val = Primitive.vm_hash_update(val, size)
    return val if Truffle::ThreadOperations.detect_outermost_recursion self do
      _attrs.each do |var|
        val = Primitive.vm_hash_update(val, Primitive.object_hidden_var_get(self, var).hash)
      end
    end
    Primitive.vm_hash_end(val)
  end

  def length
    _attrs.length
  end
  alias_method :size, :length

  def members
    Primitive.class(self).members
  end

  def to_a
    _attrs.map { |var| Primitive.object_hidden_var_get(self, var) }
  end
  alias_method :values, :to_a
  alias_method :deconstruct, :to_a

  def deconstruct_keys(keys)
    return to_h if Primitive.nil?(keys)
    raise TypeError, "wrong argument type #{Primitive.class(keys)} (expected Array or nil)" unless Primitive.is_a?(keys, Array)

    return {} if self.length < keys.length

    h = {}
    keys.each do |requested_key|
      case requested_key
      when Symbol
        symbolized_key = requested_key
      when String
        symbolized_key = requested_key.to_sym
      else
        symbolized_key = _attrs[requested_key]
      end

      if symbolized_key && _attrs.include?(symbolized_key)
        h[requested_key] = Primitive.object_hidden_var_get(self, symbolized_key)
      else
        return h
      end
    end
    h
  end

  def values_at(*args)
    out = []

    args.each do |elem|
      if Primitive.is_a?(elem, Range)
        start, length = Primitive.range_normalized_start_length(elem, size)
        finish = start + length - 1

        raise RangeError, "#{elem} out of range" if start < 0
        next if finish < start

        finish_in_bounds = [finish, _attrs.length - 1].min
        start.upto(finish_in_bounds) do |index|
          name = check_index_var!(index)
          out << Primitive.object_hidden_var_get(self, name)
        end

        (finish_in_bounds + 1).upto(finish) { out << nil }
      else
        index = Primitive.rb_num2int(elem)
        name = check_index_var!(index)
        out << Primitive.object_hidden_var_get(self, name)
      end
    end

    out
  end

  private def polyglot_read_member(name)
    symbol = name.to_sym
    if members.include? symbol
      self[symbol]
    else
      Primitive.dispatch_missing
    end
  end

  private def polyglot_write_member(name, value)
    symbol = name.to_sym
    if members.include? symbol
      self[symbol] = value
    else
      Primitive.dispatch_missing
    end
  end

  private def polyglot_member_modifiable?(name)
    if members.include? name.to_sym
      true
    else
      Primitive.dispatch_missing
    end
  end

  # other polyglot member related methods do not need to be defined since
  # the default implementation already does what we need.
  # E.g. all the members of struct members are already readable since there
  # are methods to read them

  def self._specialize(attrs)
    # People sometimes subclass Struct directly, ie.
    #
    #  class MyCoolStruct < Struct
    #
    # rather than
    #
    #  class MyCoolStruct < Struct.new(:x, :y)
    #
    # They sometimes then define their own #initialize and super into
    # Struct#initialize.
    #
    # When they do this and then do MyCoolStruct.new(:x, :y), this code
    # will accidentally shadow their #initialize. So for now, only run
    # the specialize if we're trying new Struct's directly from Struct itself,
    # not a Struct subclass.

    return unless Primitive.equal?(superclass, Struct)
    return unless attrs.map(&:to_s).all? { |a| a.ascii_only? || (a.encoding == Encoding::UTF_8 && a.valid_encoding?) }

    args, assigns, hashes, vars = [], [], [], []

    attrs.each_with_index do |name, i|
      assigns << "Primitive.object_hidden_var_set(self, #{name.inspect}, a#{i})"
      vars    << "Primitive.object_hidden_var_get(self, #{name.inspect})"
      args    << "a#{i} = nil"
      hashes  << "#{vars[-1]}.hash"
    end

    hash_calculation = hashes.map do |calc|
      "hash = Primitive.vm_hash_update hash, #{calc}"
    end.join("\n")

    code, line = <<~CODE, __LINE__+1
      # truffleruby_primitives: true
      def initialize(#{args.join(", ")})
        #{assigns.join(';')}
        self
      end

      def hash
        hash = Primitive.vm_hash_start(Primitive.class(self).hash)
        hash = Primitive.vm_hash_update hash, #{hashes.size}

        return hash if Truffle::ThreadOperations.detect_outermost_recursion(self) do
          #{hash_calculation}
        end

        Primitive.vm_hash_end hash
      end

      def to_a
        [#{vars.join(', ')}]
      end

      def length
        #{vars.size}
      end
    CODE

    begin
      mod = Module.new do
        module_eval code, __FILE__, line
      end
      include mod
    rescue SyntaxError
      # SyntaxError means that something is wrong with the
      # specialization code. Just eat the error and don't specialize.
      nil
    end
  end
end
