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
  def RARRAY_PTR(array)
    Primitive.array_store_to_native(array)
    Primitive.cext_mark_object_on_call_exit(array)
    Primitive.array_store_address(array)
  end

  # ruby.h: the flags of `struct RBasic`, used by the rb_tr_flags/rb_tr_set_flags upcalls

  RBASIC_USER_FLAGS = Primitive.object_hidden_var_create :user_flags

  # RUBY_FL* values are from ruby.h
  RUBY_FL_FREEZE = (1<<11)

  RUBY_FL_USHIFT = 12
  RBASIC_USER_FLAGS_MASK = (1 << (RUBY_FL_USHIFT + 19)) - (1 << (RUBY_FL_USHIFT))
  private_constant :RUBY_FL_USHIFT, :RBASIC_USER_FLAGS_MASK

  def rb_tr_flags(object)
    flags = 0
    flags |= RUBY_FL_FREEZE if object.frozen?
    flags | (Primitive.object_hidden_var_get(object, RBASIC_USER_FLAGS) || 0)
  end

  def rb_tr_set_flags(object, flags)
    Primitive.object_hidden_var_set(object, RBASIC_USER_FLAGS, flags & RBASIC_USER_FLAGS_MASK)
    flags &= ~RBASIC_USER_FLAGS_MASK

    # handle last!
    if flags & RUBY_FL_FREEZE != 0
      object.freeze
      flags &= ~RUBY_FL_FREEZE
    elsif object.frozen?
      raise ArgumentError, "can't unfreeze object"
    end

    raise ArgumentError, "unsupported remaining flags: #{rbasic_flags_to_string(flags)}" if flags != 0
  end

  private def rbasic_flag_to_string(flag)
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

  private def rbasic_flags_to_string(flags)
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
    decoded.map { |flag| rbasic_flag_to_string(flag) }.join(', ')
  end

  # encoding.h: `struct rb_encoding`

  # Tracks which slots of the native rb_encoding block have their name initialized.
  # Only used to initialize slots, reads of initialized slots need no lookup table.
  RB_ENCODING_INITIALIZED = []
  RB_ENCODING_INIT_MUTEX = Mutex.new

  # The address of the native rb_encoding struct for the given Encoding. All the structs are in a single native block
  # (rb_tr_encodings in encoding.c), ordered by the encoding's index, so the address is computed from the index.
  def rb_encoding_native_address(encoding)
    index = Primitive.encoding_get_encoding_index(encoding)
    unless RB_ENCODING_INITIALIZED[index]
      RB_ENCODING_INIT_MUTEX.synchronize do
        unless RB_ENCODING_INITIALIZED[index]
          # The name is stored in the native rb_encoding struct, so it must be converted to native first.
          # The name String is kept alive by the Encoding object.
          name = Primitive.cext_invoke_l_l(RSTRING_PTR_FUNCTION, Primitive.cext_wrap(encoding.name))
          Primitive.cext_invoke_v_ll(SETUP_ENCODING_FUNCTION, index, name)
          RB_ENCODING_INITIALIZED[index] = true
        end
      end
    end
    RB_ENCODINGS_BASE + index * RB_ENCODING_SIZE
  end

  # The Encoding for the address of a native rb_encoding struct, computed from the address, see above
  def rb_encoding_from_native(address)
    Primitive.encoding_get_encoding_by_index((address - RB_ENCODINGS_BASE) / RB_ENCODING_SIZE)
  end
end
