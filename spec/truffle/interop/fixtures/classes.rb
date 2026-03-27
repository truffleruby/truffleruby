# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module TruffleInteropSpecs

  class Logger
    def initialize
      @log = []
    end

    def <<(*args)
      @log << args unless @log.frozen?
    end

    def log
      # Stop recording so messages caused by spec expectations are not included
      @log.freeze
    end
  end

  class AsPointerClass
    def polyglot_pointer?
      true
    end

    def polyglot_as_pointer
      0x123
    end
  end

  class InvokeTestClass
    def add(a, b)
      a + b
    end
  end

  class InteropKeysClass
    def initialize
      @a = 1
      @b = 2
      @c = 3
    end

    def foo
      14
    end
  end

  class InteropKeysIndexClass
    def initialize
      @a = 1
      @b = 2
      @c = 3
    end

    def [](n)
      instance_variable_get(:"@#{n}")
    end

    def foo
      14
    end
  end

  class NewTestClass
    attr_reader :x

    def initialize(a, b)
      @x = a + b
    end
  end

  class ReadHasMethod
    def foo
      14
    end
  end

  class PolyglotPointer
    def initialize
      @address = nil
    end

    def polyglot_pointer?
      @address != nil
    end

    def polyglot_as_pointer
      @address or raise Truffle::Interop::UnsupportedMessageException
    end

    def polyglot_to_native
      @address = 42
    end
  end

  class PolyglotArray
    attr_reader :log

    def initialize(&value_validator)
      @log = []
      @storage = []
      @value_validator = value_validator || -> _ { true }
    end

    def polyglot_has_array_elements?
      @log << [__callee__]
      true
    end

    def polyglot_array_size
      @log << [__callee__]
      @storage.size
    end

    def polyglot_read_array_element(index)
      @log << [__callee__, index]
      raise Truffle::Interop::InvalidArrayIndexException unless in_bounds?(index)
      @storage[index]
    end

    def polyglot_write_array_element(index, value)
      @log << [__callee__, index, value]
      @value_validator.call(value) or raise Truffle::Interop::UnsupportedTypeException
      @storage[index] = value
      nil
    end

    def polyglot_remove_array_element(index)
      @log << [__callee__, index]
      raise Truffle::Interop::InvalidArrayIndexException unless in_bounds?(index)
      @storage.delete_at(index)
      nil
    end

    def polyglot_array_element_readable?(index)
      @log << [__callee__, index]
      in_bounds?(index)
    end

    def polyglot_array_element_modifiable?(index)
      @log << [__callee__, index]
      in_bounds?(index)
    end

    def polyglot_array_element_insertable?(index)
      @log << [__callee__, index]
      index >= @storage.size && Primitive.integer_fits_into_int?(index)
    end

    def polyglot_array_element_removable?(index)
      @log << [__callee__, index]
      in_bounds?(index)
    end

    private

    def in_bounds?(index)
      index >= 0 && index < @storage.size
    end
  end

  class PolyglotHash
    attr_reader :log

    def initialize
      @log = []
      @storage = {}
    end

    def polyglot_has_hash_entries?
      @log << [__callee__]
      true
    end

    def polyglot_hash_size
      @log << [__callee__]
      @storage.size
    end

    def polyglot_hash_entry_existing?(key)
      @log << [__callee__, key]
      @storage.key? key
    end

    def polyglot_hash_entry_insertable?(key)
      @log << [__callee__, key]
      !@storage.key?(key)
    end

    def polyglot_hash_entry_modifiable?(key)
      @log << [__callee__, key]
      @storage.key? key
    end

    def polyglot_hash_entry_readable?(key)
      @log << [__callee__, key]
      @storage.key? key
    end

    def polyglot_hash_entry_removable?(key)
      @log << [__callee__, key]
      @storage.key? key
    end

    def polyglot_hash_entry_writable?(key)
      @log << [__callee__, key]
      !@storage.key?(key)
    end

    def polyglot_read_hash_entry(key)
      @log << [__callee__, key]
      raise Truffle::Interop::UnknownKeyException unless @storage.key?(key)
      @storage[key]
    end

    def polyglot_write_hash_entry(key, value)
      @log << [__callee__, key, value]
      @storage[key] = value
    end

    def polyglot_remove_hash_entry(key)
      @log << [__callee__, key]
      raise Truffle::Interop::UnknownKeyException unless @storage.key?(key)
      @storage.delete(key)
    end

    def polyglot_hash_entries_iterator
      @log << [__callee__]
      @storage.each_pair
    end

    def polyglot_hash_keys_iterator
      @log << [__callee__]
      @storage.each_key
    end

    def polyglot_hash_values_iterator
      @log << [__callee__]
      @storage.each_value
    end
  end

  class PolyglotMember
    attr_reader :log

    def initialize
      @log = []
      @storage = {}
    end

    def polyglot_has_members?
      true
    end

    def polyglot_members(internal)
      @log << [__callee__, internal]
      @storage.keys
    end

    def polyglot_read_member(name)
      @log << [__callee__, name]
      @storage.fetch(name) { raise Truffle::Interop::UnknownIdentifierException }
    end

    def polyglot_write_member(name, value)
      @log << [__callee__, name, value]
      @storage[name] = value
    end

    def polyglot_remove_member(name)
      @log << [__callee__, name]
      @storage.delete(name) { raise Truffle::Interop::UnknownIdentifierException }
    end

    def polyglot_invoke_member(name, *args)
      @log << [__callee__, name, *args]
      @storage.fetch(name) { raise Truffle::Interop::UnknownIdentifierException }.call(*args)
    end

    def polyglot_member_readable?(name)
      @log << [__callee__, name]
      @storage.key? name
    end

    def polyglot_member_modifiable?(name)
      @log << [__callee__, name]
      @storage.key? name
    end

    def polyglot_member_removable?(name)
      @log << [__callee__, name]
      @storage.key? name
    end

    def polyglot_member_insertable?(name)
      @log << [__callee__, name]
      !@storage.key? name
    end

    def polyglot_member_invocable?(name)
      @log << [__callee__, name]
      @storage.key?(name) && Truffle::Interop.executable?(@storage.fetch(name))
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

  class ReadHasIndex
    attr_reader :key

    def [](n)
      @key = n
      14
    end

    def bob
      14
    end
  end

  class WriteHasMethod
    def foo=(value)
      @called = true
    end

    def called?
      @called
    end
  end

  class WriteHasIndexSet
    attr_reader :key, :value, :called

    def []=(n, value)
      @key = n
      @value = value
      @called = true
    end

    def bob
      14
    end
  end

  class WriteHasIndexSetAndIndex
    attr_reader :key, :value, :called

    def [](n)
      @value = n
    end

    def []=(n, value)
      @key = n
      @value = value
      @called = true
    end

    def bob
      14
    end
  end

end
