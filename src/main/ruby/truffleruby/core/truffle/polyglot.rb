# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Polyglot
  # stub defined in CoreLibrary
  class UnsupportedMessageError < StandardError
  end

  class InnerContext
    DEFAULT_ON_CANCELLED = -> { raise RuntimeError, 'Polyglot::InnerContext was terminated forcefully' }
    private_constant :DEFAULT_ON_CANCELLED

    # Create a new isolated inner context to eval code in any available public language
    # (those languages can be listed with +Polyglot.languages+).
    # Automatically closes the context when given a block.
    def self.new(languages: [], language_options: {}, inherit_all_access: true, code_sharing: true, on_cancelled: DEFAULT_ON_CANCELLED)
      languages = languages.map { |language| Primitive.convert_with_to_str(language) }
      language_options = language_options.flat_map { |k, v| [Primitive.convert_with_to_str(k), Primitive.convert_with_to_str(v)] }
      code_sharing = code_sharing == :inherit ? nil : Primitive.as_boolean(code_sharing)

      inner_context = Primitive.inner_context_new(self, languages, language_options, inherit_all_access, code_sharing, on_cancelled)
      if block_given?
        begin
          yield inner_context
        ensure
          inner_context.close
        end
      else
        inner_context
      end
    end

    # Eval a String of code in the given language in the inner context.
    # The return value, unless it is a Java primitive or a java.lang.String, is wrapped in a generic interop wrapper,
    # so the returned object behaves like a foreign object (even if language is 'ruby').
    # That wrapper automatically enters and leaves the inner context for any access to that object.
    def eval(language, code, filename = '(eval)')
      Primitive.inner_context_eval(self, language, code, filename)
    end
    Primitive.always_split self, :eval

    # Close the inner context and release the associated resources.
    # If the context is not closed explicitly, then it is automatically closed together with the parent context.
    def close
      Primitive.inner_context_close(self)
    end

    # Forcefully close the inner context and stops execution by throwing an exception.
    # Polyglot::InnerContext#eval can no longer be used after this operation.
    def stop
      Primitive.inner_context_close_force(self)
    end
  end

  # The list of all languages that are installed in GraalVM and publicly accessible.
  def self.languages
    Truffle::Interop.languages
  end

  def self.export(name, value)
    Truffle::Interop.export name, value
  end

  def self.export_method(name)
    Truffle::Interop.export_method name
  end

  def self.import(name)
    Truffle::Interop.import(name)
  end

  def self.import_method(name)
    Truffle::Interop.import_method name
  end

  def self.as_enumerable(object)
    Truffle::Interop.enumerable(object)
  end

  # region Trait modules for foreign objects
  # Specs for these methods are in spec/truffle/interop/special_forms_spec.rb
  # and in spec/truffle/interop/polyglot/foreign_*_spec.rb

  module HashTrait
    include Enumerable

    def each(&block)
      return to_enum(:each) { size } unless block_given?

      iterator = Truffle::Interop.hash_entries_iterator(self)
      iterator.each(&block)
      self
    end

    def each_pair
      return to_enum(:each_pair) { size } unless block_given?

      each do |key, value|
        yield key, value
      end
      self
    end

    def each_key(&block)
      return to_enum(:each_key) { size } unless block_given?

      Truffle::Interop.hash_keys_iterator(self).each(&block)
      self
    end

    def keys
      Truffle::Interop.hash_keys_iterator(self).to_a
    end

    def each_value(&block)
      return to_enum(:each_value) { size } unless block_given?

      Truffle::Interop.hash_values_iterator(self).each(&block)
      self
    end

    def values
      Truffle::Interop.hash_values_iterator(self).to_a
    end

    def [](key)
      Truffle::Interop.read_hash_value_or_default(self, key, nil)
    end

    def []=(key, value)
      Truffle::Interop.write_hash_entry(self, key, value)
    end

    def delete(key)
      value = Truffle::Interop.read_hash_value_or_default(self, key, undefined)
      if Primitive.undefined?(value)
        nil
      else
        Truffle::Interop.remove_hash_entry(self, key)
        value
      end
    end

    def fetch(key, default = undefined)
      value = Truffle::Interop.read_hash_value_or_default(self, key, undefined)
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

    def size
      Truffle::Interop.hash_size(self)
    end
    alias_method :length, :size

    def empty?
      Truffle::Interop.hash_size(self) == 0
    end

    def to_hash
      h = {}
      each_pair { |k,v| h[k] = v }
      h
    end
    alias_method :to_h, :to_hash
  end

  module ArrayTrait
    include Enumerable

    def each
      return to_enum(:each) { size } unless block_given?

      i = 0
      while i < size
        yield Truffle::Interop.read_array_element(self, i)
        i += 1
      end

      self
    end

    def at(index)
      size = Truffle::Interop.array_size(self)
      index += size if index < 0
      if index < 0 || index >= size
        nil
      else
        Truffle::Interop.read_array_element(self, index)
      end
    end

    def [](index)
      if Primitive.is_a?(index, Numeric)
        at(index)
      else
        super(index)
      end
    end

    def []=(index, value)
      if Primitive.is_a?(index, Numeric)
        Truffle::Interop.write_array_element(self, index, value)
      else
        super(index, value)
      end
    end

    def delete(index)
      if Primitive.is_a?(index, Numeric)
        Truffle::Interop.remove_array_element(self, index)
      else
        super(index)
      end
    end

    def empty?
      Truffle::Interop.array_size(self) == 0
    end

    def first
      self[0]
    end

    def last
      self[-1]
    end

    def size
      Truffle::Interop.array_size(self)
    end
    alias_method :length, :size

    def to_ary
      Truffle::Interop.to_array(self)
    end
    alias_method :to_a, :to_ary

    def ==(other)
      to_ary == other
    end

    def index(...)
      to_a.index(...)
    end

    def reverse
      to_a.reverse
    end
  end

  module ExceptionTrait
    def message
      if Truffle::Interop.has_exception_message?(self)
        Truffle::Interop.exception_message(self)
      else
        nil
      end
    end

    def cause
      if Truffle::Interop.has_exception_cause?(self)
        Truffle::Interop.exception_cause(self)
      else
        nil
      end
    end

    def exception(message = nil)
      unless Primitive.nil?(message)
        raise "ForeignException#exception currently only handles no or nil message (given #{message.inspect})"
      end
      self
    end

    def inspect
      "#{super[0...-1]}: #{message}>"
    end

    def backtrace_locations
      if Truffle::Interop.has_exception_stack_trace?(self)
        last_user_location = nil
        Truffle::Interop.exception_stack_trace(self).reverse.filter_map do |entry|
          method_name = Truffle::Interop.has_executable_name?(entry) ? Truffle::Interop.executable_name(entry).to_s : '<unknown>'

          source_location = Truffle::Interop.has_source_location?(entry) && Truffle::Interop.source_location(entry)
          if source_location && source_location.user?
            last_user_location = source_location
          elsif last_user_location
            source_location = last_user_location
          else # no source location, (unknown) or internal with no last_user_location
            source_location = nil
          end

          Truffle::Interop::BacktraceLocation.new(source_location, method_name) if source_location
        end.reverse
      else
        nil
      end
    end

    def backtrace
      backtrace_locations&.map(&:to_s)
    end

    def marshal_dump
      raise TypeError, "Foreign exception cannot be dumped: #{inspect}"
    end

    def marshal_load(array)
      raise TypeError, 'Foreign exception cannot be restored'
    end
  end

  module ExecutableTrait
    def call(*args)
      Truffle::Interop.execute(self, *args)
    end

    def to_proc
      -> (*args) { call(*args) }
    end
  end

  module InstantiableTrait
    def new(*args)
      Truffle::Interop.instantiate(self, *args)
    end
  end

  module IterableTrait
    include Enumerable

    def each(&block)
      return to_enum(:each) { size } unless block_given?

      iterator = Truffle::Interop.iterator(self)
      iterator.each(&block)
      self
    end
  end

  module IteratorTrait
    include Enumerable

    def each
      return to_enum(:each) { size } unless block_given?

      while Truffle::Interop.has_iterator_next_element?(self)
        begin
          element = Truffle::Interop.iterator_next_element(self)
        rescue StopIteration
          break
        end

        yield element
      end

      self
    end
  end

  module MetaObjectTrait
    def name
      Truffle::Interop.meta_qualified_name(self)
    end

    def ===(instance)
      Truffle::Interop.meta_instance?(self, instance)
    end
  end

  module NullTrait
    def nil?
      true
    end

    def inspect
      klass = Truffle::InteropOperations.ruby_class_and_language(self)
      "#<#{klass} null>"
    end
  end

  module NumberTrait
    def ==(other)
      Truffle::Interop.unbox(self) == other
    end

    def to_i
      if Truffle::Interop.fits_in_int?(self)
        Truffle::Interop.as_int(self)
      elsif Truffle::Interop.fits_in_long?(self)
        Truffle::Interop.as_long(self)
      elsif Truffle::Interop.fits_in_big_integer?(self)
        Truffle::Interop.as_big_integer(self)
      else
        Truffle::Interop.as_double(self).to_i
      end
    end

    def to_f
      if Truffle::Interop.fits_in_double?(self)
        Truffle::Interop.as_double(self)
      elsif Truffle::Interop.fits_in_long?(self)
        Truffle::Interop.as_long(self).to_f
      else
        Truffle::Interop.as_big_integer(self).to_f
      end
    end

    def respond_to?(name, include_all = false)
      case symbol = name.to_sym
      when :to_f
        Truffle::Interop.fits_in_double?(self) || Truffle::Interop.fits_in_big_integer?(self)
      when :to_i
        Truffle::Interop.fits_in_big_integer?(self)
      else
        super(symbol, include_all)
      end
    end
  end

  module PointerTrait
  end

  module StringTrait
    def to_s
      Primitive.foreign_string_to_ruby_string(self)
    end

    def to_str
      Primitive.foreign_string_to_ruby_string(self)
    end

    # asTruffleString() and asString() are both immutable and the only way to access a foreign string (isString())
    def freeze
      self
    end

    # asTruffleString() and asString() are both immutable and the only way to access a foreign string (isString())
    def frozen?
      true
    end
  end

  module ObjectTrait
    # Methods #__id__, #equal? are implemented for foreign objects directly in their BasicObject definition

    def inspect
      recursive_string_for(self) if Truffle::ThreadOperations.detect_recursion self do
        return Truffle::InteropOperations.foreign_inspect_nonrecursive(self)
      end
    end

    def to_s
      klass = Truffle::InteropOperations.ruby_class_and_language(self)
      # Let InteropLibrary#toDisplayString show the class and identity hash code if relevant
      if Truffle::InteropOperations.java_type?(self)
        "#<#{klass} type #{Truffle::Interop.to_display_string(self)}>"
      else
        "#<#{klass} #{Truffle::Interop.to_display_string(self)}>"
      end
    end

    def is_a?(klass)
      unless Truffle::Interop.meta_object?(klass)
        raise TypeError, 'class or module or meta object required'
      end

      if Truffle::Interop.foreign?(klass)
        Truffle::Interop.meta_instance?(klass, self)
      else
        receiver = Truffle::Interop.unbox_if_needed(self)
        Primitive.is_a?(receiver, klass)
      end
    end
    alias_method :kind_of?, :is_a?

    def instance_variable_get(member)
      Truffle::Interop.read_member(self, member)
    end

    def instance_variable_set(member, value)
      begin
        Truffle::Interop.write_member(self, member, value)
      rescue Polyglot::UnsupportedMessageError
        # the receiver does not support writing at all, e.g. it is immutable
        raise FrozenError.new("can't modify frozen #{Primitive.class(self)}", receiver: self)
      end
    end

    def instance_variables
      return [] unless Truffle::Interop.has_members?(self)
      Truffle::Interop.members(self).filter_map do |member|
        # Ruby does not have the concept of non-readable members, ignore those
        if Truffle::Interop.member_readable?(self, member) &&
            !Truffle::Interop.member_invocable?(self, member)
          member.to_sym
        end
      end
    end

    def methods(regular = true)
      if regular
        super() | Truffle::Interop.members(self).filter_map do |member|
          member.to_sym if Truffle::Interop.member_invocable?(self, member)
        end
      else
        super(regular)
      end
    end

    def [](member)
      Truffle::Interop.read_member(self, member)
    end

    def []=(member, value)
      Truffle::Interop.write_member(self, member, value)
    end

    def delete(member)
      Truffle::Interop.remove_member(self, member)
    end
  end

  class ForeignObject < Object
    include ObjectTrait

    class << self
      undef_method :new
    end

    def self.__allocate__
      raise TypeError, "allocator undefined for #{self}"
    end
  end

  class ForeignException < StandardError
    include ObjectTrait
    include ExceptionTrait

    class << self
      undef_method :new
    end

    def self.__allocate__
      raise TypeError, "allocator undefined for #{self}"
    end
  end
  # endregion
end

module Java
  def self.add_to_classpath(path)
    Primitive.java_add_to_classpath(path)
  end

  def self.type(name)
    Truffle::Interop.java_type(name)
  end

  def self.import(name)
    nesting = Primitive.caller_nesting
    mod = nesting.first || Object

    name = name.to_s
    simple_name = name.split('.').last
    type = Java.type(name)
    if mod.const_defined?(simple_name)
      current = mod.const_get(simple_name)
      if Primitive.equal?(current, type)
        # Ignore - it's already set
      else
        raise NameError, "constant #{simple_name} already set"
      end
    else
      mod.const_set simple_name, type
    end
    type
  end

  # test-unit expects `Java::JavaLang::Throwable` to be resolvable if `::Java` is defined (see assertions.rb).
  # When doing JRuby-style interop, that's a fine assumption. However, we have `::Java` defined for Truffle-style
  # interop and in that case, the assumption does not hold. In order to allow the gem to work properly, we define
  # a dummy `Throwable` class here.
  module JavaLang
    class Throwable; end
  end
end
