# frozen_string_literal: true

# Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module Interop

    # stubs, defined in CoreLibrary
    # rubocop:disable Lint/InheritException
    class InteropException < Exception; end
    class UnsupportedMessageException < InteropException; end
    class InvalidArrayIndexException < InteropException; end
    class UnknownIdentifierException < InteropException; end
    class UnsupportedTypeException < InteropException; end
    class UnknownKeyException < InteropException; end
    class ArityException < InteropException
      attr_reader :min_expected, :max_expected

      def initialize(min_expected, max_expected = min_expected)
        raise ArgumentError unless Primitive.is_a?(min_expected, Integer)
        raise ArgumentError unless Primitive.is_a?(max_expected, Integer)
        @min_expected = min_expected
        @max_expected = max_expected
      end
    end

    class SourceLocation
      def user?
        available? and !internal?
      end

      def to_s
        "#{path}:#{lineno}"
      end

      def inspect
        "#{super[0...-1]} #{self}>"
      end
    end

    # Like Thread::Backtrace::Location but based on a SourceLocation
    class BacktraceLocation
      attr_reader :source_location, :label
      alias_method :base_label, :label

      def initialize(source_location, label)
        @source_location = source_location
        @label = label
      end

      def path
        @source_location.path
      end

      def absolute_path
        @source_location.absolute_path
      end

      def lineno
        @source_location.lineno
      end

      def to_s
        label = @label || '<unknown>'
        "#{@source_location}:in '#{label}'"
      end

      def inspect
        to_s.inspect
      end
    end

    def self.import_method(name)
      method = import(name.to_s)

      Object.define_method(name.to_sym) do |*args|
        Truffle::Interop.execute(method, *args)
      end
    end

    def self.export_method(name)
      export(name.to_s, Object.method(name.to_sym))
    end

    def self.get_members_implementation(object, internal)
      keys = []

      if object.respond_to?(:polyglot_members, true)
        keys = object.__send__(:polyglot_members, internal)
      else
        add_method_key = proc do |method|
          # do not list methods which cannot be read using interop
          keys << method.to_s if Primitive.respond_to? object, method, true
        end

        object.public_methods.each(&add_method_key)

        if internal
          object.instance_variables.each do |ivar|
            ivar_string = ivar.to_s
            keys << ivar_string if ivar_string.start_with?('@')
          end

          object.protected_methods.each(&add_method_key)
          object.private_methods.each(&add_method_key)
        end
      end

      keys
    end
    private_class_method :get_members_implementation

    class HashKeysAsPolyglotMembers
      def initialize(hash)
        raise ArgumentError, 'expected a Hash' unless Primitive.is_a?(hash, Hash)
        # Otherwise Symbol keys won't be seen from e.g., readMember()
        @hash = hash.transform_keys do |key|
          if Primitive.is_a?(key, Symbol)
            key.to_s
          else
            key
          end
        end
      end

      private

      def polyglot_has_members?
        true
      end

      def polyglot_members(internal)
        @hash.keys
      end

      def polyglot_read_member(name)
        @hash.fetch(name) { raise Truffle::Interop::UnknownIdentifierException }
      end

      def polyglot_write_member(name, value)
        @hash[name] = value
      end

      def polyglot_remove_member(name)
        @hash.delete(name) { raise Truffle::Interop::UnknownIdentifierException }
      end

      def polyglot_invoke_member(name, *args)
        @hash.fetch(name) { raise Truffle::Interop::UnknownIdentifierException }.call(*args)
      end

      def polyglot_member_readable?(name)
        @hash.key? name
      end

      def polyglot_member_modifiable?(name)
        @hash.key? name
      end

      def polyglot_member_removable?(name)
        @hash.key? name
      end

      def polyglot_member_insertable?(name)
        !@hash.key? name
      end

      def polyglot_member_invocable?(name)
        value = @hash.fetch(name) { return false }
        Truffle::Interop.executable?(value)
      end

      def polyglot_member_internal?(name)
        false
      end

      def polyglot_has_member_read_side_effects?(name)
        false
      end

      def polyglot_has_member_write_side_effects?(name)
        false
      end
    end

    def self.hash_keys_as_members(hash)
      HashKeysAsPolyglotMembers.new(hash)
    end

    class ForeignEnumerable
      include Enumerable
      attr_reader :foreign

      def initialize(foreign)
        @foreign = foreign
      end

      def each
        (0...size).each do |i|
          yield foreign[i]
        end
      end

      def size
        Truffle::Interop.array_size(foreign)
      end
    end

    def self.enumerable(foreign)
      ForeignEnumerable.new(foreign)
    end

    def self.from_java_string(string)
      Primitive.foreign_string_to_ruby_string(string)
    end

    def self.to_java_string(string)
      Primitive.to_java_string(string)
    end

    def self.java_array(*array)
      to_java_array(array)
    end

    def self.to_java_array(array)
      java_array = Primitive.interop_to_java_array(array)
      if !Primitive.undefined?(java_array)
        java_array
      else
        to_java_array(Truffle::Type.coerce_to(array, Array, :to_a))
      end
    end

    def self.to_java_list(array)
      list = Primitive.interop_to_java_list(array)
      if !Primitive.undefined?(list)
        list
      else
        to_java_list(Truffle::Type.coerce_to(array, Array, :to_a))
      end
    end

    def self.to_array(object)
      unless Truffle::Interop.has_array_elements?(object)
        raise 'foreign object returns false for hasArrayElements() and cannot be converted into an array'
      end

      Array.new(Truffle::Interop.array_size(object)) do |i|
        Truffle::Interop.read_array_element(object, i)
      end
    end

    def self.from_java_array(array)
      to_array(array)
    end

    def self.to_hash(object)
      Hash[*Truffle::InteropOperations.pairs_from_object(object)]
    end

    def self.unbox_if_needed(object)
      if Truffle::Interop.foreign?(object) && Truffle::Interop.boxed?(object)
        Truffle::Interop.unbox(object)
      else
        object
      end
    end

    def self.boxed?(object)
      boolean?(object) || fits_in_big_integer?(object) || fits_in_double?(object)
    end

    # Only used by unbox_if_needed and NumberTrait
    def self.unbox(object)
      return as_boolean object if boolean? object

      if number?(object)
        return as_int object if fits_in_int? object
        return as_long object if fits_in_long? object
        return as_big_integer object if fits_in_big_integer? object
        return as_double object if fits_in_double? object
      end

      raise ArgumentError, "not boxed: #{object}"
    end

    def self.to_java_map(hash)
      map = ::Java.type('java.util.HashMap').new
      hash.each do |key, value|
        map.put key, value
      end
      map
    end
  end
end
