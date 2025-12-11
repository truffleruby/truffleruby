# frozen_string_literal: true

# Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module DataOperations
    def self.unknown_keywords_message(given, object)
      unknowns = given - Primitive.class(object)::CLASS_MEMBERS
      s = 's' if unknowns.size > 1
      "unknown keyword#{s}: #{unknowns.map(&:inspect).join ', '}"
    end

    def self.missing_keywords_message(given, object)
      missing = Primitive.class(object)::CLASS_MEMBERS - given
      s = 's' if missing.size > 1
      "missing keyword#{s}: #{missing.map(&:inspect).join ', '}"
    end
  end
end

class Data
  # Defined to help deserialize Data instances, see https://github.com/ruby/psych/pull/765
  # Keep in sync with #initialize below
  def initialize(**kwargs)
    members_hash = Primitive.class(self)::CLASS_MEMBERS_HASH
    kwargs.each do |member, value|
      member = member.to_sym
      if members_hash.include?(member)
        Primitive.object_hidden_var_set(self, member, value)
      else
        raise ArgumentError, Truffle::DataOperations.unknown_keywords_message(kwargs.keys, self)
      end
    end

    if kwargs.size < members_hash.size
      raise ArgumentError, Truffle::DataOperations.missing_keywords_message(kwargs.keys, self)
    end
    Primitive.freeze(self)
  end

  # The entire public API of Data is this single class method
  def self.define(*class_members, &block)
    members_hash = {}
    class_members.each do |m|
      member = Truffle::Type.symbol_or_string_to_symbol(m)

      raise ArgumentError, "invalid data member: #{member}" if member.end_with?('=')
      raise ArgumentError, "duplicate member: #{member}" if members_hash[member]
      members_hash[member] = true
    end

    members = members_hash.keys
    members.freeze
    members_hash.freeze

    klass = Class.new self do
      const_set :CLASS_MEMBERS, members
      const_set :CLASS_MEMBERS_HASH, members_hash

      def self.members
        self::CLASS_MEMBERS.dup
      end

      class << self
        define_method(:__allocate__, BasicObject.method(:__allocate__))

        undef_method :define
      end

      def self.new(*args, **kwargs)
        if !args.empty? and !kwargs.empty?
          raise ArgumentError, "wrong number of arguments (given #{args.size + 1}, expected 0)"
        end

        instance = allocate

        if !kwargs.empty?
          instance.send(:initialize, **kwargs)
        else
          if args.size > self::CLASS_MEMBERS.size
            raise ArgumentError, "wrong number of arguments (given #{args.size}, expected 0..#{self::CLASS_MEMBERS.size})"
          end

          kwargs_for_initialize = {}
          args.each_with_index do |arg, i|
            kwargs_for_initialize[self::CLASS_MEMBERS[i]] = arg
          end

          instance.send(:initialize, **kwargs_for_initialize)
        end

        instance
      end
      singleton_class.alias_method :[], :new

      # As an exception, these instance methods are directly defined on the returned class, like in CRuby.
      # The reason is CRuby does not use an extra module so it needs to define these instance methods on the returned class.
      members.each do |member|
        define_method(member) { Primitive.object_hidden_var_get(self, member) }
      end
    end

    # Instance methods are defined in an included module, so it is possible, e.g.,
    # to redefine #initialize in the returned Data subclass and use super() to use the #initialize just below.
    # CRuby defines these directly on Data, but that is suboptimal for performance.
    # We want to have a specialized copy of these methods for each Data subclass.
    instance_methods_module = Module.new
    instance_methods_module.module_eval "#{<<~'RUBY'}", __FILE__, __LINE__+1
      # truffleruby_primitives: true

      ORIGINAL_NEW = klass.method(:new)
      private_constant :ORIGINAL_NEW

      # Keep in sync with #initialize above
      def initialize(**kwargs)
        members_hash = Primitive.class(self)::CLASS_MEMBERS_HASH
        kwargs.each do |member, value|
          member = member.to_sym
          if members_hash.include?(member)
            Primitive.object_hidden_var_set(self, member, value)
          else
            raise ArgumentError, Truffle::DataOperations.unknown_keywords_message(kwargs.keys, self)
          end
        end

        if kwargs.size < members_hash.size
          raise ArgumentError, Truffle::DataOperations.missing_keywords_message(kwargs.keys, self)
        end
        Primitive.freeze(self)
      end

      def initialize_copy(other)
        Primitive.class(other)::CLASS_MEMBERS.each do |member|
          Primitive.object_hidden_var_set self, member, Primitive.object_hidden_var_get(other, member)
        end
        Primitive.freeze(self)
        self
      end

      def members
        Primitive.class(self).members
      end

      def to_h(&block)
        h = {}
        Primitive.class(self)::CLASS_MEMBERS.each do |member|
          h[member] = Primitive.object_hidden_var_get(self, member)
        end
        block ? h.to_h(&block) : h
      end

      def with(**changes)
        return self if changes.empty?

        ORIGINAL_NEW.call(**to_h.merge(changes))
      end

      def inspect
        klass = Primitive.class(self)

        return "#<data #{klass}:...>" if Truffle::ThreadOperations.detect_recursion(self) do
          class_name = Primitive.module_anonymous?(klass) ? nil : Primitive.module_name(klass)

          members_and_values = to_h.map do |member, value|
            if Truffle::CExt.rb_is_local_id(member) or Truffle::CExt.rb_is_const_id(member)
              "#{member}=#{value.inspect}"
            else
              "#{member.inspect}=#{value.inspect}"
            end
          end

          return "#<data #{class_name}#{' ' if class_name}#{members_and_values.join(', ')}>"
        end
      end
      alias_method :to_s, :inspect

      def deconstruct
        Primitive.class(self)::CLASS_MEMBERS.map do |member|
          Primitive.object_hidden_var_get(self, member)
        end
      end

      def deconstruct_keys(keys)
        return to_h if Primitive.nil?(keys)
        raise TypeError, "wrong argument type #{Primitive.class(keys)} (expected Array or nil)" unless Primitive.is_a?(keys, Array)

        members_hash = Primitive.class(self)::CLASS_MEMBERS_HASH
        return {} if members_hash.size < keys.size

        h = {}
        members = Primitive.class(self)::CLASS_MEMBERS
        keys.each do |requested_key|
          case requested_key
          when Symbol
            symbolized_key = requested_key
          when String
            symbolized_key = requested_key.to_sym
          else
            symbolized_key = members[requested_key]
          end

          if symbolized_key && members_hash.include?(symbolized_key)
            h[requested_key] = Primitive.object_hidden_var_get(self, symbolized_key)
          else
            return h
          end
        end
        h
      end

      def ==(other)
        return true if Primitive.equal?(self, other)
        return false unless Primitive.class(self) == Primitive.class(other)

        Truffle::ThreadOperations.detect_pair_recursion self, other do
          return self.deconstruct == other.deconstruct
        end

        # Subtle: if we are here, we are recursing and haven't found any difference, so:
        true
      end

      def eql?(other)
        return true if Primitive.equal?(self, other)
        return false unless Primitive.class(self) == Primitive.class(other)

        Truffle::ThreadOperations.detect_pair_recursion self, other do
          return self.deconstruct.eql?(other.deconstruct)
        end

        # Subtle: if we are here, we are recursing and haven't found any difference, so:
        true
      end

      def hash
        klass = Primitive.class(self)
        members = klass::CLASS_MEMBERS

        val = Primitive.vm_hash_start(klass.hash)
        val = Primitive.vm_hash_update(val, members.size)

        return val if Truffle::ThreadOperations.detect_outermost_recursion self do
          members.each do |member|
            member_hash = Primitive.object_hidden_var_get(self, member).hash
            val = Primitive.vm_hash_update(val, member_hash)
          end
        end

        Primitive.vm_hash_end(val)
      end
    RUBY

    klass.include instance_methods_module

    klass.module_eval(&block) if block

    klass
  end

  class << self
    undef_method :new
  end

  def self.__allocate__
    raise TypeError, "allocator undefined for #{self}"
  end
end
