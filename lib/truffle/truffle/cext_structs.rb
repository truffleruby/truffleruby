# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# This file includes the Ruby definition of C structs defined in ruby headers (or made available through it, for
# instance as function return value).

module Truffle::CExt
  def RBASIC(object)
    if Primitive.immediate_value?(object)
      raise TypeError, "immediate values don't include the RBasic struct"
    end
    RBasic.new(object)
  end

  def RARRAY_PTR(array)
    RArrayPtr.new(array)
  end
end

# ruby.h: `struct RBasic`
class Truffle::CExt::RBasic
  USER_FLAGS = Primitive.object_hidden_var_create :user_flags

  # RUBY_FL* values are from ruby.h
  RUBY_FL_TAINT = (1<<8)
  RUBY_FL_FREEZE = (1<<11)

  RUBY_FL_USHIFT = 12
  USER_FLAGS_MASK = (1 << (RUBY_FL_USHIFT + 19)) - (1 << (RUBY_FL_USHIFT))
  private_constant :RUBY_FL_USHIFT, :USER_FLAGS_MASK

  def initialize(object)
    @object = object
  end

  def user_flags
    Primitive.object_hidden_var_get(@object, USER_FLAGS) || 0
  end

  def set_user_flags(flags)
    Primitive.object_hidden_var_set(@object, USER_FLAGS, flags)
  end

  def compute_flags
    flags = 0
    flags |= RUBY_FL_FREEZE if @object.frozen?
    flags | user_flags
  end

  def flag_to_string(flag)
    case flag
    when 1<<5;        'RUBY_FL_PROMOTED (1<<5)'
    when 1<<6;        'RUBY_FL_UNUSED6 (1<<6)'
    when 1<<7;        'RUBY_FL_FINALIZE (1<<7)'
    when 1<<8;        'RUBY_FL_SHAREABLE (1<<8)'
    when 1<<9;        'RUBY_FL_SEEN_OBJ_ID (1<<9)'
    when 1<<10;       'RUBY_FL_EXIVAR (1<<10)'
    when 1<<11;       'RUBY_FL_FREEZE (1<<11)'
    when 12;          'RUBY_FL_USHIFT (12)'
    else;             "unknown flag (#{flag})"
    end
  end

  def flags_to_string(flags)
    ushift   = flags[2] == 1 && flags[3] == 1
    promoted = flags[5] == 1 && flags[6] == 1

    decoded = (0...flags.bit_length).reject do |i|
      flags[i] == 0 ||
          ushift && (i == 2 || i == 3) ||
          promoted && (i == 5 || i == 6)
    end
    decoded = decoded.map { |i| 1 << i }

    decoded << (1<<5 | 1<<6) if promoted
    decoded << (1<<2 | 1<<3) if ushift
    decoded.map(&method(:flag_to_string)).join(', ')
  end

  def set_flags(flags)
    if flags & RUBY_FL_TAINT != 0
      # noop
      flags &= ~RUBY_FL_TAINT
    end

    set_user_flags(flags & USER_FLAGS_MASK)
    flags &= ~USER_FLAGS_MASK

    # handle last!
    if flags & RUBY_FL_FREEZE != 0
      @object.freeze
      flags &= ~RUBY_FL_FREEZE
    elsif @object.frozen?
      raise ArgumentError, "can't unfreeze object"
    end

    raise ArgumentError, "unsupported remaining flags: #{flags_to_string(flags)}" if flags != 0
  end
end

# ruby.h: `struct RArray`
class Truffle::CExt::RArrayPtr
  attr_reader :array

  def initialize(array)
    @array = array
  end

  def polyglot_pointer?
    Primitive.array_store_native?(@array)
  end

  def polyglot_as_pointer
    Primitive.cext_mark_object_on_call_exit(@array)
    Primitive.array_store_address(@array)
  end

  def polyglot_to_native
    Primitive.array_store_to_native(@array)
  end

  def polyglot_has_array_elements?
    true
  end

  def polyglot_array_size
    @array.size
  end

  def polyglot_read_array_element(index)
    Primitive.cext_wrap(@array[index])
  end

  def polyglot_write_array_element(index, value)
    @array[index] = Primitive.cext_unwrap(value)
  end

  def polyglot_array_element_readable?(index)
    index >= 0 && index < @array.size
  end

  def polyglot_array_element_modifiable?(index)
    index >= 0 && index < @array.size
  end

  def polyglot_array_element_insertable?(index)
    false
  end

  def polyglot_array_element_removable?(index)
    false
  end
end

# encoding.h: `struct rb_encoding`
class Truffle::CExt::RbEncoding
  ENCODING_CACHE = Array.new(Encoding.list.size, nil) # Encoding index => RbEncoding
  NATIVE_CACHE = {} # RbEncoding address => RbEncoding
  ENCODING_CACHE_MUTEX = Mutex.new

  private_class_method :new

  def self.get(encoding)
    index = Primitive.encoding_get_encoding_index(encoding)
    rb_encoding = ENCODING_CACHE[index]
    if rb_encoding
      rb_encoding
    else
      ENCODING_CACHE_MUTEX.synchronize do
        rb_encoding = ENCODING_CACHE[index]
        if rb_encoding
          rb_encoding
        else
          ENCODING_CACHE[index] = new(encoding)
        end
      end
    end
  end

  def self.get_encoding_from_native(rbencoding_ptr)
    ENCODING_CACHE_MUTEX.synchronize do
      NATIVE_CACHE[rbencoding_ptr].encoding
    end
  end

  attr_reader :encoding

  def initialize(encoding)
    @encoding = encoding
    @pointer = nil
    @name = Truffle::CExt::LIBTRUFFLERUBY.rb_tr_rstring_ptr(Primitive.cext_wrap(encoding.name))
  end

  private

  def polyglot_has_members?
    true
  end

  def polyglot_members(internal)
    ['name']
  end

  def polyglot_read_member(name)
    raise Truffle::Interop::UnknownIdentifierException unless name == 'name'
    @name
  end

  def polyglot_write_member(name, value)
    raise Truffle::Interop::UnsupportedMessageException
  end

  def polyglot_remove_member(name)
    raise Truffle::Interop::UnsupportedMessageException
  end

  def polyglot_invoke_member(name, *args)
    raise Truffle::Interop::UnsupportedMessageException
  end

  def polyglot_member_readable?(name)
    name == 'name'
  end

  def polyglot_member_modifiable?(name)
    false
  end

  def polyglot_member_removable?(name)
    false
  end

  def polyglot_member_insertable?(name)
    false
  end

  def polyglot_member_invocable?(name)
    false
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

  def polyglot_pointer?
    !Primitive.nil?(@pointer)
  end

  def polyglot_to_native
    unless @pointer
      ENCODING_CACHE_MUTEX.synchronize do
        unless @pointer
          @pointer = Truffle::CExt::LIBTRUFFLERUBY.rb_encoding_to_native(@name)
          NATIVE_CACHE[Truffle::Interop.as_pointer(@pointer)] = self
        end
      end
    end
  end

  def polyglot_as_pointer
    pointer = @pointer
    raise Truffle::Interop::UnsupportedMessageException if Primitive.nil?(pointer)
    Truffle::Interop.as_pointer(pointer)
  end
end
