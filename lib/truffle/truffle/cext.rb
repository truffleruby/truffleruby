# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative 'cext_ruby'
require_relative 'cext_constants'
require_relative 'cext_structs'

# TODO (eregon, 1 Sep 2022): should we always_split or use separate CallTargets for usages of Primitive.call_with* in this file?

module Truffle::CExt
  DATA_TYPE = Primitive.object_hidden_var_create :data_type
  DATA_STRUCT = Primitive.object_hidden_var_create :data_struct # struct RData* or struct RTypedData*
  DATA_MARKER = Primitive.object_hidden_var_create :data_marker
  DATA_MEMSIZER = Primitive.object_hidden_var_create :data_memsizer
  RB_TYPE = Primitive.object_hidden_var_create :rb_type
  ALLOCATOR_FUNC = Primitive.object_hidden_var_create :allocator_func
  RB_IO_STRUCT = Primitive.object_hidden_var_create :rb_io_struct

  SULONG = Truffle::Boot.get_option('cexts-sulong')
  NFI = !SULONG

  CEXT_LOCK = Truffle::Boot.get_option('cexts-lock')

  SET_LIBTRUFFLERUBY = -> libtruffleruby do
    LIBTRUFFLERUBY = libtruffleruby
  end

  SET_LIBTRAMPOLINE = -> libtrampoline do
    LIBTRAMPOLINE = libtrampoline
  end

  SETUP_WRAPPERS = -> lib do
    # These wrappers must be used when calling native extension functions,
    # so that setjmp() is done before calling the native extension function,
    # and if the native extension function (potentially through other functions) calls a Ruby C API function
    # and if that throws a Java exception, then on return from that Ruby C API function a longjmp()
    # will be done to unwind the native stack.
    # It is essential that only native frames and no Java frames are unwinded with longjmp(),
    # which means these wrappers must be used exactly when calling into native functions, not before or after.
    # Sulong-executed C code counts as not-native in this context.

    # The signature starts with an extra pointer which is for passing the native function to call
    POINTER_TO_POINTER_WRAPPERS = [nil] + (1..16).map do |n|
      sig = -"(pointer,#{(['pointer'] * n).join(',')}):pointer"
      Primitive.interop_eval_nfi(sig).bind(lib[:"rb_tr_setjmp_wrapper_pointer#{n}_to_pointer"])
    end

    POINTER_TO_POINTER_WRAPPER = POINTER_TO_POINTER_WRAPPERS[1]
    POINTER2_TO_POINTER_WRAPPER = POINTER_TO_POINTER_WRAPPERS[2]
    POINTER3_TO_POINTER_WRAPPER = POINTER_TO_POINTER_WRAPPERS[3]

    VOID_TO_VOID_WRAPPER = Primitive.interop_eval_nfi('(pointer):void').bind(lib[:rb_tr_setjmp_wrapper_void_to_void])
    POINTER_TO_VOID_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer):void').bind(lib[:rb_tr_setjmp_wrapper_pointer1_to_void])
    POINTER2_TO_VOID_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer,pointer):void').bind(lib[:rb_tr_setjmp_wrapper_pointer2_to_void])
    POINTER3_TO_VOID_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer,pointer,pointer):void').bind(lib[:rb_tr_setjmp_wrapper_pointer3_to_void])
    POINTER3_TO_INT_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer,pointer,pointer):sint32').bind(lib[:rb_tr_setjmp_wrapper_pointer3_to_int])
    POINTER_TO_SIZE_T_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer):uint64').bind(lib[:rb_tr_setjmp_wrapper_pointer1_to_size_t])
    INT_POINTER2_TO_POINTER_WRAPPER = Primitive.interop_eval_nfi('(pointer,sint32,pointer,pointer):pointer').bind(lib[:rb_tr_setjmp_wrapper_int_pointer2_to_pointer])
    POINTER2_INT_TO_POINTER_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer,pointer,sint32):pointer').bind(lib[:rb_tr_setjmp_wrapper_pointer2_int_to_pointer])
    POINTER2_INT_POINTER2_TO_POINTER_WRAPPER = Primitive.interop_eval_nfi('(pointer,pointer,pointer,sint32,pointer,pointer):pointer').bind(lib[:rb_tr_setjmp_wrapper_pointer2_int_pointer2_to_pointer])
    RB_BLOCK_CALL_FUNC_WRAPPER = POINTER2_INT_POINTER2_TO_POINTER_WRAPPER

    RB_DEFINE_METHOD_WRAPPERS = [
      # argc=0 still means passing (VALUE self) for rb_define_method
      # argc >= 0 # (VALUE obj); (VALUE obj, VALUE arg1); (VALUE obj, VALUE arg1, VALUE arg2); ...
      *POINTER_TO_POINTER_WRAPPERS[1..-1],
      nil,
      # argc == -2 # (VALUE obj, VALUE rubyArrayArgs)
      POINTER_TO_POINTER_WRAPPERS[2],
      # argc == -1 # (int argc, VALUE *argv, VALUE obj)
      INT_POINTER2_TO_POINTER_WRAPPER
    ]
  end

  extend self

  T_NONE     = 0x00

  T_OBJECT   = 0x01
  T_CLASS    = 0x02
  T_MODULE   = 0x03
  T_FLOAT    = 0x04
  T_STRING   = 0x05
  T_REGEXP   = 0x06
  T_ARRAY    = 0x07
  T_HASH     = 0x08
  T_STRUCT   = 0x09
  T_BIGNUM   = 0x0a
  T_FILE     = 0x0b
  T_DATA     = 0x0c
  T_MATCH    = 0x0d
  T_COMPLEX  = 0x0e
  T_RATIONAL = 0x0f

  T_NIL      = 0x11
  T_TRUE     = 0x12
  T_FALSE    = 0x13
  T_SYMBOL   = 0x14
  T_FIXNUM   = 0x15
  T_UNDEF    = 0x16

  T_IMEMO    = 0x1a
  T_NODE     = 0x1b
  T_ICLASS   = 0x1c
  T_ZOMBIE   = 0x1d

  T_MASK     = 0x1f

  RUBY_FIXNUM_MIN = -(1 << 62)
  RUBY_FIXNUM_MAX = (1 << 62) - 1

  # This list of types is derived from MRI's error.c builtin_types array.
  # It has the same indices as the ruby_value_type enum.
  BUILTIN_TYPES = [
    '',
    'Object',
    'Class',
    'Module',
    'Float',
    'String',
    'Regexp',
    'Array',
    'Hash',
    'Struct',
    'Integer',
    'File',
    'Data',
    'MatchData',
    'Complex',
    'Rational',
    '',
    'nil',
    'true',
    'false',
    'Symbol',
    'Integer',
    'undef',
    '',
    '',
    '',
    'Memo',
    'Node',
    'iClass'
  ]

  RUBY_ENC_CODERANGE_UNKNOWN = 0
  RUBY_ENC_CODERANGE_7BIT = 1
  RUBY_ENC_CODERANGE_VALID = 2
  RUBY_ENC_CODERANGE_BROKEN = 4

  RUBY_ECONV_INVALID_MASK = Encoding::Converter::INVALID_MASK
  RUBY_ECONV_INVALID_REPLACE = Encoding::Converter::INVALID_REPLACE
  RUBY_ECONV_UNDEF_MASK = Encoding::Converter::UNDEF_MASK
  RUBY_ECONV_UNDEF_REPLACE = Encoding::Converter::UNDEF_REPLACE
  RUBY_ECONV_UNDEF_HEX_CHARREF = Encoding::Converter::UNDEF_HEX_CHARREF
  RUBY_ECONV_UNIVERSAL_NEWLINE_DECORATOR = Encoding::Converter::UNIVERSAL_NEWLINE_DECORATOR
  RUBY_ECONV_CRLF_NEWLINE_DECORATOR = Encoding::Converter::CRLF_NEWLINE_DECORATOR
  RUBY_ECONV_CR_NEWLINE_DECORATOR = Encoding::Converter::CR_NEWLINE_DECORATOR
  RUBY_ECONV_XML_TEXT_DECORATOR = Encoding::Converter::XML_TEXT_DECORATOR
  RUBY_ECONV_XML_ATTR_CONTENT_DECORATOR = Encoding::Converter::XML_ATTR_CONTENT_DECORATOR
  RUBY_ECONV_XML_ATTR_QUOTE_DECORATOR = Encoding::Converter::XML_ATTR_QUOTE_DECORATOR
  RUBY_ECONV_PARTIAL_INPUT = Encoding::Converter::PARTIAL_INPUT
  RUBY_ECONV_AFTER_OUTPUT = Encoding::Converter::AFTER_OUTPUT

  # Rails 6 overrides `Time.at` in a way that doesn't delegate keyword arguments to the original `Time.at` method.
  # The issue was fixed in Rails 6.1 but wasn't backported.
  # It causes ArgumentError exception when `rb_time_timespec_new` function is called in the `pg` gem.
  # See https://github.com/rails/rails/issues/47586.
  ORIGINAL_TIME_AT = Time.method(:at)

  def self.init_libtruffleruby(libtruffleruby)
    SET_LIBTRUFFLERUBY.call(libtruffleruby)

    libtruffleruby.rb_tr_init(Truffle::CExt)

    SETUP_WRAPPERS.call(libtruffleruby) if SULONG
  end

  def self.init_libtrufflerubytrampoline(libtrampoline)
    keep_alive = []

    # rb_tr_longwrap_sig = Primitive.interop_eval_nfi('(sint64):pointer')
    # rb_tr_longwrap = rb_tr_longwrap_sig.createClosure(self.rb_tr_wrap_function)
    #
    # globals_args = [
    #   rb_tr_longwrap
    # ]
    # keep_alive << globals_args
    #
    # init_globals = libtrampoline['rb_tr_trampoline_init_globals']
    # init_globals = Primitive.interop_eval_nfi("(#{Array.new(globals_args.size, 'pointer').join(',')}):void").bind(init_globals)
    # init_globals.call(*globals_args)

    init_functions = libtrampoline[:rb_tr_trampoline_init_functions]
    init_functions = Primitive.interop_eval_nfi('(env,(string):pointer):void').bind(init_functions)

    panama = Truffle::Boot.get_option('cexts-panama') && Primitive.vm_java_version >= 22 && !TruffleRuby.native?
    if panama
      # Check if the Panama backend is available, it might not be when embedding TruffleRuby
      rb_tr_invoke = LIBTRUFFLERUBY['rb_tr_invoke']
      begin
        keep_alive << rb_tr_invoke.createNativeClosure('panama')
      rescue Polyglot::ForeignException => e
        warn "warning: the Panama Truffle NFI backend for running C extensions faster is not available (#{e.message}). Add 'org.graalvm.truffle:truffle-nfi-panama' to Maven dependencies to resolve or use '--ruby.cexts-panama=false' to ignore."
        panama = false
      end
    end

    if panama
      init_functions.call(-> name {
        closure = LIBTRUFFLERUBY[name].createNativeClosure('panama')
        keep_alive << closure
        closure
      })
    else
      init_functions.call(-> name { LIBTRUFFLERUBY[name] })
    end

    init_constants = libtrampoline[:rb_tr_trampoline_init_global_constants]
    init_constants = Primitive.interop_eval_nfi('((string):pointer):void').bind(init_constants)
    # TODO: use a Hash instead to be faster? (or Array with fixed indices)
    init_constants.call(-> name { value = Truffle::CExt.send(name); keep_alive << value; Primitive.cext_wrap(value) })

    LIBTRUFFLERUBY.set_rb_tr_rb_f_notimplement(libtrampoline[:rb_f_notimplement])

    SET_LIBTRAMPOLINE.call(libtrampoline)

    SETUP_WRAPPERS.call(libtrampoline)

    @init_libtrufflerubytrampoline_keep_alive = keep_alive.freeze
  end

  def init_extension(library, library_path)
    name = File.basename(library_path)
    # We need the substring before the first dot, for "cool.io_ext.so" -> "Init_cool"
    i = name.index('.')
    name = name[0...i] if i
    function_name = "Init_#{name}"

    init_function = library[function_name]

    Primitive.call_with_cext_lock_and_frame(-> {
      begin
        Primitive.interop_execute(VOID_TO_VOID_WRAPPER, [init_function])
      ensure
        # Resolve while inside the ExtensionCallStackEntry to ensure the preservedObjects are still all alive
        resolve_registered_addresses
      end
    }, [], nil, nil, CEXT_LOCK)
  end

  def supported?
    Interop.mime_type_supported?('application/x-sulong-library')
  end

  def check_abi_version(embedded_abi_version, extension_path)
    runtime_abi_version = Truffle::GemUtil::ABI_VERSION
    if embedded_abi_version != runtime_abi_version
      message = "The native extension at #{extension_path} has a different ABI version: #{embedded_abi_version.inspect} " \
        "than the running TruffleRuby: #{runtime_abi_version.inspect}"
      warn message, uplevel: 1
      raise LoadError, message
    end
  end

  def rb_tr_cext_lock_owned_p
    Primitive.cext_lock_owned?
  end

  def rb_stdin
    $stdin
  end

  def rb_stdout
    $stdout
  end

  def rb_stderr
    $stderr
  end

  def rb_fs
    $;
  end

  def rb_output_fs
    $,
  end

  def rb_rs
    $/
  end

  def rb_output_rs
    $\
  end

  def rb_default_rs
    "\n"
  end

  def rb_to_symbol(name)
    case name
    when Symbol
      name
    else
      converted = Truffle::Type.rb_check_convert_type(name , String, :to_str)
      raise TypeError, "#{name} is not a symbol" unless converted
      converted.to_sym
    end
  end

  def rb_type(value)
    # TODO CS 23-Jul-16 we could do with making this a kind of specialising case
    # that puts never seen cases behind a transfer

    value_class = Primitive.class(value)
    type = Primitive.object_hidden_var_get(value_class, RB_TYPE)
    type ||= Primitive.object_hidden_var_set(value_class, RB_TYPE, rb_tr_find_type(value))
    rb_tr_cached_type(value, type)
  end

  def rb_tr_cached_type(value, type)
    if type == T_NONE
      if Primitive.object_hidden_var_defined?(value, DATA_STRUCT)
        T_DATA
      else
        T_OBJECT
      end
    elsif type == T_FIXNUM
      if RUBY_FIXNUM_MIN <= value && value <= RUBY_FIXNUM_MAX
        T_FIXNUM
      else
        T_BIGNUM
      end
    else
      type
    end
  end

  def rb_tr_find_type(value)
    case value
    when Class
      T_CLASS
    when Module
      T_MODULE
    when Float
      T_FLOAT
    when String
      T_STRING
    when Regexp
      T_REGEXP
    when Array
      T_ARRAY
    when Hash
      T_HASH
    when Struct
      T_STRUCT
    when IO
      T_FILE
    when Complex
      T_COMPLEX
    when Rational
      T_RATIONAL
    when NilClass
      T_NIL
    when TrueClass
      T_TRUE
    when FalseClass
      T_FALSE
    when Symbol
      T_SYMBOL
    when Integer
      # See #rb_tr_cached_type, the final type must be calculated for each number.
      T_FIXNUM
    when Time
      T_DATA
    when BasicObject
      # See #rb_tr_cached_type, the final type must be calculated for each object.
      T_NONE
    else
      raise "unknown type #{Primitive.class(value)}"
    end
  end

  def RB_TYPE_P(value, type)
    rb_type(value) == type
  end

  def rb_check_type(value, type)
    value_type = rb_type(value)
    if value_type != type || (value_type == T_DATA && Primitive.object_hidden_var_defined?(value, DATA_TYPE))
      raise TypeError, "wrong argument type #{Truffle::ExceptionOperations.to_class_name(value)} (expected #{BUILTIN_TYPES[type]})"
    end
  end

  def RTYPEDDATA_P(obj)
    Primitive.object_hidden_var_defined?(obj, DATA_TYPE)
  end

  def ensure_class(obj, klass, message = 'expected class %s, but object class is %s')
    raise TypeError, format(message, klass, Primitive.class(obj)) unless Primitive.is_a?(obj, klass)
  end

  def rb_method_boundp(klass, id, ex)
    if ex == 0
      (klass.method_defined?(id) || klass.private_method_defined?(id) || klass.protected_method_defined?(id)) ? 1 : 0
    else
      klass.method_defined?(id) ? 1 : 0
    end
  end

  def rb_obj_is_instance_of(object, ruby_class)
    Primitive.class(object) == ruby_class
  end

  def rb_obj_is_kind_of(object, ruby_class)
    Primitive.is_a?(object, ruby_class)
  end

  def SYMBOL_P(value)
    Primitive.is_a?(value, Symbol)
  end

  def rb_to_int(val)
    Primitive.rb_to_int(val)
  end

  def rb_fix2int(value)
    Primitive.rb_num2int(value)
  end

  def rb_fix2uint(value)
    Truffle::Type.rb_num2uint(value)
  end

  def rb_fix2str(value, base)
    value.to_s(base)
  end

  def RB_FLOAT_TYPE_P(value)
    Primitive.is_a?(value, Float)
  end

  def rb_integer_type_p(value)
    Primitive.ruby_integer?(value)
  end

  def rb_require(feature)
    require feature
  end

  def RB_OBJ_TAINTABLE(object)
    case object
    when TrueClass, FalseClass, NilClass, Integer, Float, Symbol
      false
    else
      true
    end
  end

  def rb_obj_clone(object)
    object.clone
  end

  def rb_obj_dup(object)
    object.dup
  end

  def rb_obj_encoding(object)
    object.encoding
  end

  FREEZE_METHOD = Kernel.instance_method :freeze

  def rb_obj_freeze(obj)
    FREEZE_METHOD.bind(obj).call
  end

  def rb_obj_frozen_p(object)
    Primitive.frozen?(object)
  end

  def rb_obj_id(object)
    object.object_id
  end

  def rb_float_new(value)
    value.to_f
  end

  def rb_flt_rationalize(value)
    value.rationalize
  end

  def rb_flt_rationalize_with_prec(value, prec)
    value.rationalize(prec)
  end

  def rb_absint_singlebit_p(val)
    Primitive.rb_int_singlebit_p(val.abs)
  end

  def rb_num2int(val)
    Primitive.rb_num2int(val)
  end

  def rb_num2long(val)
    Primitive.rb_num2long(val)
  end

  def rb_big_sign(val)
    val >= 0
  end

  def rb_big_cmp(x, y)
    x <=> y
  end

  def rb_big2dbl(val)
    Truffle::Type.rb_big2dbl(val)
  end

  def rb_big2long(val)
    Truffle::Type.rb_big2long(val)
  end

  def rb_big2ulong(val)
    Truffle::Type.rb_big2long(val)
  end

  def rb_dbl2big(val)
    val.to_i
  end

  def rb_num_coerce_bin(x, y, func)
    a, b = do_coerce(x, y, true)
    a.__send__(func, b)
  end

  def rb_num_coerce_cmp(x, y, func)
    ary = do_coerce(x, y, false)
    if Primitive.nil?(ary)
      nil
    else
      a, b = ary
      a.__send__(func, b)
    end
  end

  def rb_num_coerce_relop(x, y, func)
    ary = do_coerce(x, y, false)
    unless Primitive.nil?(ary)
      a, b = ary
      res = a.__send__(func, b)
    end
    raise ArgumentError, "comparison of #{Primitive.class(x)} with #{Primitive.class(y)} failed" if Primitive.nil?(res)
    res
  end

  private def do_coerce(x, y, raise_error)
    unless y.respond_to?(:coerce)
      if raise_error
        raise TypeError, "#{Primitive.class(y)} can't be coerced to #{Primitive.class(x)}"
      else
        return nil
      end
    end

    ary = y.coerce(x)
    if Primitive.nil?(ary) && !raise_error
      return nil
    end
    if !Primitive.is_a?(ary, Array) || ary.size != 2
      raise TypeError, 'coerce must return [x, y]'
    end
    ary
  end

  def rb_num2uint(val)
    Truffle::Type.rb_num2uint(val)
  end

  def rb_num2ulong(val)
    # We're going to create a signed long here, and rely on the C to
    # cast it to an unsigned one.
    Truffle::Type.rb_num2ulong(val)
  end

  def rb_num2dbl(val)
    Truffle::Type.rb_num2dbl(val)
  end

  def rb_Integer(value)
    Integer(value)
  end

  def rb_Float(value)
    Float(value)
  end

  def RFLOAT_VALUE(value)
    value
  end

  def rb_hash_start(h)
    Primitive.vm_hash_start(h)
  end

  def rb_obj_class(object)
    Primitive.class(object)
  end

  def rb_obj_classname(object)
    Primitive.class(object).name
  end

  def rb_class_of(object)
    Primitive.metaclass(object)
  end

  def rb_singleton_class(object)
    object.singleton_class
  end

  def rb_class_real(ruby_class)
    while ruby_class.singleton_class?
      ruby_class = ruby_class.superclass
    end

    ruby_class
  end

  def rb_class_get_superclass(ruby_class)
    return false unless Primitive.is_a?(ruby_class, Class)
    ruby_class.superclass || false
  end

  def rb_obj_respond_to(object, name, priv)
    object.respond_to?(name, priv != 0)
  end

  def rb_respond_to(object, name)
    object.respond_to?(name, false)
  end

  def rb_check_convert_type(obj, type_name, method)
    Truffle::Type.rb_check_convert_type(obj, Object.const_get(type_name), method.to_sym)
  end

  def rb_convert_type(obj, type_name, method)
    Truffle::Type.rb_convert_type(obj, Object.const_get(type_name), method.to_sym)
  end

  def rb_check_to_integer(obj, method)
    Truffle::Type.rb_check_to_integer(obj, method.to_sym)
  end

  def rb_obj_method_arity(object, id)
    object.method(id).arity
  end

  def rb_obj_method(object, id)
    object.method(id)
  end

  def rb_ivar_defined(object, id)
    Primitive.object_ivar_defined?(object, id)
  end

  def rb_f_global_variables
    Kernel.global_variables
  end

  def rb_ivar_foreach(object, func, arg)
    keys_and_vals = []
    if Primitive.is_a?(object, Module)
      object.class_variables.each do |key|
        keys_and_vals << key
        keys_and_vals << object.class_variable_get(key)
      end
    end
    object.instance_variables.each do |key|
      keys_and_vals << key
      keys_and_vals << object.instance_variable_get(key)
    end

    keys_and_vals.each_slice(2) do |key, val|
      st_result = Primitive.interop_execute(POINTER3_TO_INT_WRAPPER,
        [func, Primitive.cext_sym2id(key), Primitive.cext_wrap(val), arg])

      case st_result
      when ST_CONTINUE
      when ST_STOP then break
      else raise ArgumentError, "Unknown 'func' return value: #{st_result}"
      end
    end
  end

  def rb_obj_instance_variables(object)
    object.instance_variables
  end

  def rb_copy_generic_ivar(clone, original)
    Primitive.check_frozen clone
    original_ivars = original.instance_variables
    rb_free_generic_ivar(clone)
    original_ivars.each do |var|
      clone.instance_variable_set(var, original.instance_variable_get(var))
    end
  end

  def rb_free_generic_ivar(original)
    Primitive.check_frozen original
    original_ivars = original.instance_variables
    original_ivars.each do |var|
      original.__send__ :remove_instance_variable, var
    end
  end

  def rb_inspect(object)
    Truffle::Type.rb_inspect(object)
  end

  def rb_range_new(beg, last, exclude_end)
    Range.new(beg, last, exclude_end != 0)
  end

  def rb_reg_new(pattern, options)
    Regexp.new(pattern, options)
  end

  def rb_reg_compile(pattern, options)
    err = nil
    result = begin
               Regexp.new(pattern, options)
             rescue => err
               nil
             end
    if err
      Primitive.fiber_set_error_info(err)
      nil
    else
      result
    end
  end

  def rb_reg_new_str(str, options)
    Regexp.new(str, options)
  end

  def rb_marshal_dump(obj, port)
    Marshal.dump(obj, port)
  end

  def rb_marshal_load(port)
    Marshal.load(port)
  end

  def rb_reg_regcomp(str)
    Regexp.compile(str)
  end

  def rb_reg_match_pre(match)
    match.pre_match
  end

  def rb_reg_nth_match(nth, match)
    return nil if Primitive.nil?(match)
    match[nth]
  end

  def rb_reg_options(re)
    re.options
  end

  def ascii8bit_encoding
    Encoding::ASCII_8BIT
  end

  def usascii_encoding
    Encoding::US_ASCII
  end

  def utf8_encoding
    Encoding::UTF_8
  end

  def rb_default_external_encoding
    Encoding.find('external')
  end

  def rb_default_internal_encoding
    Encoding.find('internal')
  end

  def rb_locale_encoding
    Encoding::LOCALE
  end

  def rb_filesystem_encoding
    Encoding.filesystem
  end

  def rb_to_encoding_index(enc)
    enc = Truffle::Type.coerce_to_encoding(enc)
    return -1 if Primitive.false?(enc)
    rb_enc_to_index(enc)
  end

  def rb_locale_encindex
    rb_enc_to_index Encoding::LOCALE
  end

  def rb_filesystem_encindex
    rb_enc_to_index Encoding.filesystem
  end

  def rb_ascii8bit_encindex
    rb_enc_to_index Encoding::ASCII_8BIT
  end

  def rb_usascii_encindex
    rb_enc_to_index Encoding::US_ASCII
  end

  def rb_utf8_encindex
    rb_enc_to_index Encoding::UTF_8
  end

  def rb_enc_from_index(index)
    Primitive.encoding_get_encoding_by_index index
  end

  def rb_enc_find_index(name)
    enc = Encoding.try_convert name
    if enc
      Primitive.encoding_get_encoding_index(enc)
    else
      -1
    end
  end

  def rb_enc_to_index(enc)
    Primitive.encoding_get_encoding_index enc
  end

  def rb_define_dummy_encoding(name)
    _, idx = Truffle::EncodingOperations.dummy_encoding(name)
    idx
  end

  def rb_str_new_frozen(value)
    if value.frozen?
      value
    else
      value.dup.freeze
    end
  end

  def rb_tracepoint_new(events, func, data)
    use_cext_lock = Primitive.use_cext_lock?
    TracePoint.new(*events_to_events_array(events)) do |tp|
      Primitive.call_with_cext_lock_and_frame(
        POINTER2_TO_VOID_WRAPPER,
        [func, Primitive.cext_wrap(tp), data],
        Primitive.caller_special_variables_if_available,
        nil,
        use_cext_lock)
    end
  end

  def events_to_events_array(events)
    events_ary = []
    parse_event = -> bits, name do
      if events.anybits? bits
        events ^= bits
        if Primitive.is_a?(name, Symbol)
          events_ary << name
        else
          warn "warning: rb_tracepoint_new(#{name}) is not yet implemented" if $VERBOSE
          events_ary << :never
        end
      end
    end

    parse_event[0x0001, :line]
    parse_event[0x0002, :class]
    parse_event[0x0004, :end]
    parse_event[0x0008, :call]
    parse_event[0x0010, :return]
    parse_event[0x0020, :c_call]
    parse_event[0x0040, :c_return]
    parse_event[0x0080, :raise]

    parse_event[0x0100, :b_call]
    parse_event[0x0200, :b_return]
    parse_event[0x0400, :thread_begin]
    parse_event[0x0800, :thread_end]
    parse_event[0x1000, :fiber_switch]
    parse_event[0x2000, :script_compiled]

    parse_event[0x100000, 'RUBY_INTERNAL_EVENT_NEWOBJ']
    parse_event[0x200000, 'RUBY_INTERNAL_EVENT_FREEOBJ']

    raise ArgumentError, "unknown event #{'%#x' % events}" unless events == 0
    events_ary
  end

  def rb_thread_alone
    Thread.list.count == 1 ? 1 : 0
  end

  def rb_intern(str)
    Primitive.string_to_symbol(str, true)
  end

  def rb_int_cmp(a, b)
    a <=> b
  end

  def rb_int_positive_pow(a, b)
    a ** b
  end

  def rb_cstr_to_inum(string, base, raise)
    Primitive.string_to_inum string, base, raise != 0, true
  end

  def rb_cstr_to_dbl(string, badcheck)
    result = Primitive.string_to_f string
    if Primitive.nil?(result)
      if badcheck
        raise ArgumentError, "invalid value for Float(): #{string.inspect}"
      else
        0.0
      end
    else
      result
    end
  end

  def rb_str_new_native(pointer, length)
    raise "#{pointer} not a pointer" unless Truffle::Interop.pointer?(pointer)
    Truffle::FFI::Pointer.new(pointer).read_string(length)
  end

  def rb_enc_str_coderange(str)
    cr = Primitive.string_get_coderange str
    coderange_java_to_rb(cr)
  end

  def coderange_java_to_rb(cr)
    case cr
    when 0
      RUBY_ENC_CODERANGE_UNKNOWN
    when 1
      RUBY_ENC_CODERANGE_7BIT
    when 2
      RUBY_ENC_CODERANGE_VALID
    when 3
      RUBY_ENC_CODERANGE_BROKEN
    else
      raise "Cannot convert coderange #{cr} to rb code range"
    end
  end

  def RB_ENC_CODERANGE(obj)
    if Primitive.is_a?(obj, String)
      rb_enc_str_coderange(obj)
    else
      raise "Unknown coderange for obj with class `#{Primitive.class(obj)}`"
    end
  end

  def rb_enc_associate_index(obj, idx)
    enc = rb_enc_from_index(idx)
    case obj
    when String
      obj.force_encoding(enc)
    else
      raise "rb_enc_associate_index not implemented for class `#{Primitive.class(obj)}`"
    end
  end

  def rb_enc_set_index(obj, idx)
    enc = rb_enc_from_index(idx)
    case obj
    when String
      obj.force_encoding enc
    else
      obj.instance_variable_set :@encoding, enc
    end
  end

  def rb_enc_get(obj)
    case obj
    when Encoding
      obj
    when Symbol
      obj.encoding
    when String
      obj.encoding
    when Regexp
      obj.encoding
    else
      obj.instance_variable_get :@encoding
    end
  end

  def rb_enc_get_index(obj)
    enc = case obj
          when Symbol
            obj.encoding
          when String
            obj.encoding
          when Regexp
            obj.encoding
          when File
            obj.internal_encoding || obj.external_encoding
          when NilClass, Integer, Float, TrueClass, FalseClass
            -1
          # TODO BJF Mar-9-2017 Handle T_DATA
          else
            if obj.instance_variable_defined?(:@encoding)
              obj.instance_variable_get(:@encoding)
            else
              0
            end
          end
    enc = rb_enc_to_index(enc) if Primitive.is_a?(enc, Encoding)
    enc
  end

  def rb_str_append(str, to_append)
    Primitive.string_append(str, to_append)
  end

  def rb_str_concat(str, to_concat)
    str << to_concat
  end

  def rb_str_encode(str, to, ecflags, ecopts)
    opts = {}
    opts.merge!(ecopts) unless Primitive.nil?(ecopts)

    # TODO BJF 8-Mar-2017 Handle more ecflags
    if ecflags & Encoding::Converter::INVALID_REPLACE != 0
      opts[:invalid] = :replace
    end

    str.encode(to, **opts)
  end

  def rb_str_conv_enc_opts(str, from, to, ecflags, ecopts)
    if (to.ascii_compatible? && str.ascii_only?) || to == Encoding::ASCII_8BIT || to.dummy?
      if str.encoding != to
        str = str.dup
        str.force_encoding(to)
      end
      return str
    end

    ec = Encoding::Converter.new(from, to, ecopts || ecflags)
    dest = +''
    # This C API will (unlike primitive convert) not alter the source
    # string, so we need to duplicate it.
    status = ec.primitive_convert str.dup, dest, nil, nil
    status == :finished ? dest : str
  end

  def rb_cmpint(val, a, b)
    raise ArgumentError, "comparison of #{Primitive.class(a)} and #{Primitive.class(b)} failed" if Primitive.nil?(val)
    if val > 0
      1
    elsif val < 0
      -1
    else
      0
    end
  end

  def rb_funcall_with_block(recv, meth, argv, block)
    Primitive.public_send_argv_without_cext_lock(recv, meth, argv, block)
  end

  def rb_funcall_with_block_keywords(recv, meth, argv, block)
    Primitive.public_send_argv_keywords_without_cext_lock(recv, meth, argv, block)
  end

  def rb_funcallv_public(recv, meth, argv)
    Primitive.public_send_argv_without_cext_lock(recv, meth, argv, nil)
  end

  def rb_funcallv(recv, meth, argv)
    Primitive.send_argv_without_cext_lock(recv, meth, argv, nil)
  end

  def rb_check_funcall(recv, meth, args)
    Primitive.send_without_cext_lock(Truffle::Type, :check_funcall, [recv, meth, args], nil)
  end

  def rb_funcallv_keywords(recv, meth, argv)
    Primitive.send_argv_keywords_without_cext_lock(recv, meth, argv, nil)
  end

  def rb_funcall(recv, meth, n, *args)
    Primitive.send_without_cext_lock(recv, meth, args, nil)
  end

  def rb_apply(recv, meth, args)
    Primitive.send_without_cext_lock(recv, meth, args, nil)
  end

  def rb_define_attr(klass, name, read, write)
    if read != 0 && write != 0
      klass.class_eval { attr_accessor name }
    elsif read != 0
      klass.class_eval { attr_reader name }
    elsif write != 0
      klass.class_eval { attr_writer name }
    end
  end

  def rb_make_backtrace
    caller
  end

  def rb_string_value_cstr_check(string)
    raise ArgumentError, 'string contains null byte' if string.include?(0.chr(string.encoding))
  end

  def rb_String(value)
    String(value)
  end

  def rb_Array(value)
    Array(value)
  end

  def rb_Hash(value)
    Hash(value)
  end

  def rb_ary_new
    []
  end

  def rb_ary_new_capa(capacity)
    if capacity < 0
      raise ArgumentError, 'negative array size (or size too big)'
    end
    []
  end

  def rb_hash_new
    {}
  end

  def rb_hash_new_capa(capacity)
    raise RuntimeError, 'st_table too big' if capacity < 0
    {}
  end

  def rb_ident_hash_new
    {}.compare_by_identity
  end

  def rb_hash_get_or_undefined(hash, key)
    Primitive.hash_get_or_undefined(hash, key)
  end

  def rb_hash_set_ifnone(hash, value)
    hash.default = value
  end

  ST_CONTINUE = 0
  ST_STOP = 1
  ST_DELETE = 2
  ST_CHECK = 3
  ST_REPLACE = 4

  def rb_hash_foreach(hash, func, farg)
    hash.each do |key, value|
      st_result = Primitive.interop_execute(POINTER3_TO_INT_WRAPPER,
        [func, Primitive.cext_wrap(key), Primitive.cext_wrap(value), farg])

      case st_result
      when ST_CONTINUE
      when ST_CHECK
      when ST_STOP then break
      when ST_DELETE then hash.delete(key)
      else raise ArgumentError, "Unknown 'func' return value: #{st_result}"
      end
    end
  end
  Primitive.always_split self, :rb_hash_foreach

  def rb_path_to_class(path)
    begin
      const = Object.const_get(path, false)
    rescue NameError
      raise ArgumentError, "undefined class/module #{path}"
    end
    unless Primitive.is_a?(const, ::Module)
      raise TypeError, "#{path} does not refer to class/module"
    end
    const
  end

  def rb_proc_new(function, value)
    use_cext_lock = Primitive.use_cext_lock?
    Proc.new do |*args, &block|
      Primitive.call_with_cext_lock_and_frame_and_unwrap(RB_BLOCK_CALL_FUNC_WRAPPER, [
        function,
        Primitive.cext_wrap(args.first), # yieldarg
        Primitive.cext_wrap(value), # procarg,
        args.size, # argc
        Truffle::CExt.RARRAY_PTR(args), # argv
        Primitive.cext_wrap(block), # blockarg
      ], Primitive.caller_special_variables_if_available, nil, use_cext_lock)
    end
  end

  def rb_proc_call(prc, args)
    prc.call(*args)
  end

  # The idea of rb_protect and rb_jump_tag is to avoid unwinding the
  # native stack in an uncontrolled manner. To do this we need to be
  # able to run a piece of code and capture both its result (if it
  # produces one), and any exception it generates. This is done by
  # running the requested code in a block as follows
  #
  #   e = store_exception { res = requested_function(...) }
  #
  # leaving us with e containing any exception thrown from the block,
  # and res containing the result of the function if it completed
  # successfully. Since Ruby's API only allows us to use a number to
  # indicate there was an exception we have to store it in a thread
  # local array and provide a 1-based index into that array. Then once
  # the native library has unwound its own stack by whatever method,
  # and can allow the ruby error to propagate, it can call rb_jump_tag
  # with the integer produced by rb_protect. rb_jump_tag then gets the
  # exception out of the thread local and calls raise exception to
  # throw it and allow normal error handling to continue.

  def rb_protect(function, arg, write_status, status)
    # We wrap nil here to avoid wrapping any result returned, as the
    # function called will do that. In general we try not to touch the
    # values passed in or out of protected functions as C extensions
    # may accept or return arbitrary pointers rather than ruby VALUEs.
    res = Primitive.cext_wrap(nil)
    pos = 0
    e = capture_exception do
      res = Primitive.interop_execute(POINTER_TO_POINTER_WRAPPER, [function, arg])
    end

    unless Primitive.nil?(e)
      store_exception(e)
      pos = extract_tag(e)
      Primitive.fiber_set_error_info(extract_ruby_exception(e))
    end

    Truffle::Interop.execute_without_conversion(write_status, status, pos)
    res
  end
  Primitive.always_split self, :rb_protect

  def rb_jump_tag(pos)
    if pos > 0
      e = retrieve_exception
      tag = extract_tag(e)
      raise RuntimeError, 'mismatch between jump tag and captured exception' unless pos == tag
      Primitive.fiber_set_error_info(nil)
      raise_exception(e)
    end
  end

  def rb_yield(value)
    rb_block_proc.call(value)
  end
  Primitive.always_split self, :rb_yield

  def rb_yield_splat(values)
    rb_block_proc.call(*values)
  end
  Primitive.always_split self, :rb_yield_splat

  def rb_ivar_lookup(object, name, default_value)
    # TODO CS 24-Jul-16 races - needs a new primitive or be defined in Java?
    if Primitive.object_ivar_defined?(object, name)
      Primitive.object_ivar_get(object, name)
    else
      default_value
    end
  end

  def rb_cvar_defined(cls, id)
    id_s = id.name
    if id_s.start_with?('@@') || !id_s.start_with?('@')
      cls.class_variable_defined?(id)
    else
      cls.instance_variable_defined?(id)
    end
  end

  def rb_cv_get(cls, name)
    cls.class_variable_get(name.to_sym)
  end

  def rb_cv_set(cls, name, val)
    cls.class_variable_set(name.to_sym, val)
  end

  def rb_cvar_get(cls, id)
    cls.class_variable_get(id)
  end

  def rb_cvar_set(cls, id, val)
    cls.class_variable_set(id, val)
  end

  def rb_exc_raise(exception)
    raise exception
  end

  def rb_set_errinfo(error)
    if Primitive.nil?(error) || Primitive.is_a?(error, Exception)
      Primitive.fiber_set_error_info(error)
    else
      raise TypeError, 'assigning non-exception to ?!'
    end
  end

  def rb_make_exception(args)
    Truffle::ExceptionOperations.make_exception(args)
  end

  def rb_errinfo
    Primitive.fiber_get_error_info
  end

  def rb_arity_error_string(arg_count, min, max)
    Truffle::Type.arity_error_string(arg_count, min, max)
  end

  def rb_raise(object, name)
    raise 'not implemented'
  end

  def rb_ivar_count(object)
    object.instance_variables.size
  end

  def rb_ivar_get(object, name)
    Primitive.object_ivar_get object, name
  end

  def rb_ivar_set(object, name, value)
    Primitive.object_ivar_set object, name, value
  end

  def rb_special_const_p(object)
    Truffle::Type.is_special_const?(object)
  end

  def rb_id2str(sym)
    sym.name
  end

  def rb_id_attrset(sym)
    :"#{sym}="
  end

  def rb_const_defined?(mod, name)
    Primitive.module_const_defined? mod, name, true, false
  end

  def rb_const_remove(mod, name)
    Primitive.module_remove_const(mod, name)
  end

  def rb_class_new(superclass)
    Primitive.class_new(superclass, false, nil)
  end

  def rb_define_class_under(mod, name, superclass)
    # nil is TypeError (checked below), false is ArgumentError
    if Primitive.false?(superclass)
      raise ArgumentError, "no super class for '#{name}'"
    end

    if Primitive.module_const_defined?(mod, name, false, false)
      current_class = Primitive.module_const_get(mod, name, false, false, false)
      unless Primitive.class(current_class) == Class
        raise TypeError, "#{mod}::#{name} is not a class"
      end
      if superclass != current_class.superclass
        raise TypeError, "superclass mismatch for class #{name}"
      end
      current_class
    else
      rb_const_set(mod, name, Class.new(superclass))
    end
  end

  def rb_define_module_under(mod, name)
    if Primitive.module_const_defined?(mod, name, false, false)
      val = Primitive.module_const_get(mod, name, false, false, false)
      unless Primitive.class(val) == Module
        raise TypeError, "#{mod}::#{name} is not a module"
      end
      val
    else
      rb_const_set mod, name, Module.new
    end
  end

  def rb_define_method_undefined(mod, name)
    mod.define_method(name) do |*|
      raise NotImplementedError, "#{name}() function is unimplemented on this machine"
    end
    Primitive.method_unimplement(mod.instance_method(name))
  end

  def rb_class_new_instance(klass, args)
    obj = klass.send(:__allocate__)
    obj.send(:initialize, *args)
    obj
  end

  def rb_class_new_instance_kw(klass, args)
    *args, kwargs = args
    kwargs = Truffle::Type.rb_convert_type kwargs, Hash, :to_hash

    obj = klass.send(:__allocate__)
    obj.send(:initialize, *args, **kwargs)
    obj
  end

  def rb_f_sprintf(args)
    sprintf(*args)
  end

  def rb_str_format(args, format)
    sprintf(format, *args)
  end

  def rb_io_printf(out, args)
    out.printf(*args)
  end

  def rb_io_print(out, args)
    Truffle::IOOperations.print out, args, nil
  end

  def rb_io_puts(out, args)
    Truffle::IOOperations.puts out, args
  end

  def rb_io_descriptor(io)
    Primitive.io_fd(io)
  end

  def rb_equal(a, b)
    Primitive.same_or_equal?(a, b)
  end

  def rb_tr_obj_equal(a, b)
    Primitive.equal?(a, b)
  end

  def rb_obj_call_init(obj, args, block)
    obj.__send__ :initialize, *args, &block
  end

  def rb_obj_instance_eval(obj, args, block)
    obj.instance_eval(*args, &block)
  end

  def rb_enumeratorize(obj, meth, args)
    obj.to_enum(meth, *args)
  end

  def rb_enumeratorize_with_size(obj, meth, args, size_fn)
    return rb_enumeratorize(obj, meth, args) if Primitive.interop_null?(size_fn)
    use_cext_lock = Primitive.use_cext_lock?
    enum = obj.to_enum(meth, *args) do
      Primitive.call_with_cext_lock_and_frame_and_unwrap(
        POINTER3_TO_POINTER_WRAPPER,
        [size_fn, Primitive.cext_wrap(obj), Primitive.cext_wrap(args), Primitive.cext_wrap(enum)],
        Primitive.caller_special_variables_if_available,
        nil,
        use_cext_lock)
    end
    enum
  end

  def rb_eval_string(str)
    eval(str)
  end

  def rb_define_alloc_func(ruby_class, function)
    use_cext_lock = Primitive.use_cext_lock?
    ruby_class.singleton_class.define_method(:__allocate__) do
      Primitive.call_with_cext_lock_and_frame_and_unwrap(
        POINTER_TO_POINTER_WRAPPER,
        [function, Primitive.cext_wrap(self)],
        Primitive.caller_special_variables_if_available,
        nil,
        use_cext_lock)
    end
    class << ruby_class
      private :__allocate__
    end
    Primitive.object_hidden_var_set(ruby_class.singleton_class, ALLOCATOR_FUNC, function)
  end

  def rb_get_alloc_func(ruby_class)
    return nil unless Primitive.is_a?(ruby_class, Class)
    begin
      allocate_method = ruby_class.method(:__allocate__).owner
    rescue NameError
      nil # it's fine to call this on a class that doesn't have an allocator
    else
      Primitive.object_hidden_var_get(allocate_method, ALLOCATOR_FUNC)
    end
  end

  def rb_undef_alloc_func(ruby_class)
    ruby_class.singleton_class.send(:undef_method, :__allocate__)
  rescue NameError
    nil # it's fine to call this on a class that doesn't have an allocator
  else
    Primitive.object_hidden_var_set(ruby_class.singleton_class, ALLOCATOR_FUNC, nil)
  end

  def rb_tr_set_default_alloc_func(ruby_class, alloc_function)
    Primitive.object_hidden_var_set(ruby_class.singleton_class, ALLOCATOR_FUNC, alloc_function)
  end

  def rb_tr_default_alloc_func(ruby_class)
    ruby_class.__send__(:__layout_allocate__)
  end

  def rb_alias(mod, new_name, old_name)
    mod.send(:alias_method, new_name, old_name)
  end

  def rb_undef(mod, name)
    if mod.frozen? or mod.method_defined?(name) or mod.private_method_defined?(name)
      mod.send(:undef_method, name)
    end
  end

  def rb_attr(ruby_class, name, read, write, ex)
    if ex.zero?
      private = false
      protected = false
      module_function = false
    else
      private = caller_frame_visibility(:private)
      protected = caller_frame_visibility(:protected)
      module_function = caller_frame_visibility(:module_function)
    end

    if read
      ruby_class.class_exec do
        attr_reader name
        private name if private
        protected name if protected
        module_function name if module_function
      end
    end

    if write
      ruby_class.class_exec do
        attr_writer name
        setter_name = :"#{name}="
        private setter_name if private
        protected setter_name if protected
        module_function setter_name if module_function
      end
    end
  end

  def rb_Rational(num, den)
    Rational(num, den)
  end

  def rb_rational_raw(num, den)
    Rational.__send__(:new_already_canonical, num, den)
  end

  def rb_rational_new(num, den)
    Rational(num, den)
  end

  def rb_Complex(real, imag)
    Complex.new(real, imag)
  end

  def rb_complex_raw(real, imag)
    Complex.new(real, imag)
  end

  def rb_complex_new(real, imag)
    Complex(real, imag)
  end

  def rb_complex_polar(r, theta)
    Complex.new(r, theta)
  end

  def rb_complex_set_real(complex, real)
    complex.__send__ :real=, real
  end

  def rb_complex_set_imag(complex, imag)
    complex.__send__ :imag=, imag
  end

  def rb_mutex_new
    Mutex.new
  end

  def rb_mutex_locked_p(mutex)
    mutex.locked?
  end

  def rb_mutex_trylock(mutex)
    mutex.try_lock
  end

  def rb_mutex_lock(mutex)
    mutex.lock
  end

  def rb_mutex_unlock(mutex)
    mutex.unlock
  end

  def rb_mutex_sleep(mutex, timeout)
    mutex.sleep(timeout)
  end

  def rb_mutex_synchronize(mutex, func, arg)
    mutex.synchronize do
      Primitive.interop_execute(POINTER_TO_POINTER_WRAPPER, [func, arg])
    end
  end
  Primitive.always_split self, :rb_mutex_synchronize

  def rb_gc_enable
    GC.enable
  end

  def rb_gc_disable
    GC.disable
  end

  def rb_gc
    GC.start
  end

  GC_ROOTS = []

  def rb_gc_register_mark_object(value)
    # We save the ValueWrapper here and not the actual value/object, this is important for primitives like double and
    # not-fixnum-long, as we need to preserve the handle by preserving the ValueWrapper of that handle.
    # For those cases the primitive cannot itself reference its ValueWrapper, unlike RubyDynamicObject and ImmutableRubyObject.
    wrapper = Primitive.cext_to_wrapper(value)
    GC_ROOTS.push wrapper
  end

  def rb_gc_latest_gc_info(hash_or_key)
    case hash_or_key
    when Symbol
      if hash_or_key == :state
        :none
      else
        raise ArgumentError, "unknown key: #{hash_or_key}"
      end
    when Hash
      hash_or_key[:state] = :none
      hash_or_key
    else
      raise TypeError, 'non-hash or symbol given'
    end
  end

  def rb_nativethread_self
    Thread.current
  end

  NATIVETHREAD_LOCKS = {}

  def rb_nativethread_lock_initialize
    lock = Mutex.new
    NATIVETHREAD_LOCKS[lock] = nil
    lock
  end

  def rb_nativethread_lock_destroy(lock)
    NATIVETHREAD_LOCKS.delete(lock)
  end

  def rb_set_end_proc(func, data)
    use_cext_lock = Primitive.use_cext_lock?
    at_exit do
      Primitive.call_with_cext_lock_and_frame(
        POINTER_TO_VOID_WRAPPER, [func, data],
        Primitive.caller_special_variables_if_available, nil, use_cext_lock)
    end
  end

  def mark_object_on_call_exit(object)
    Primitive.cext_mark_object_on_call_exit(object)
  end

  def RDATA(object)
    # A specialized version of rb_check_type(object, T_DATA)
    data_struct = Primitive.object_hidden_var_get(object, DATA_STRUCT)
    unless data_struct
      raise TypeError, "wrong argument type #{Primitive.class(object)} (expected T_DATA)"
    end
    data_struct
  end

  def RTYPEDDATA(object)
    # A specialized version of rb_check_type(object, T_DATA)
    data_struct = Primitive.object_hidden_var_get(object, DATA_STRUCT)
    unless data_struct
      raise TypeError, "wrong argument type #{Primitive.class(object)} (expected T_DATA)"
    end
    data_struct
  end

  private def data_marker(marker_function, struct)
    if Truffle::Interop.null?(marker_function)
      nil
    else
      -> { Primitive.interop_execute(marker_function, [struct]) }
    end
  end

  private def data_sizer(sizer_function, rtypeddata)
    use_cext_lock = Primitive.use_cext_lock?
    proc {
      Primitive.call_with_cext_lock_and_frame(
        sizer_function, [rtypeddata],
        Primitive.caller_special_variables_if_available, nil, use_cext_lock)
    }
  end

  def rb_data_object_wrap(ruby_class, data, mark, free)
    ruby_class = Object unless ruby_class
    object = ruby_class.__send__(:__layout_allocate__)
    use_cext_lock = Primitive.use_cext_lock?

    rdata = LIBTRUFFLERUBY.rb_tr_rdata_create(mark, free, data)
    Primitive.object_hidden_var_set object, DATA_STRUCT, rdata
    Primitive.object_hidden_var_set object, DATA_MARKER, data_marker(LIBTRUFFLERUBY[:rb_tr_rdata_run_marker], rdata)
    # Could use a simpler finalizer if Truffle::Interop.null?(free)
    Primitive.objectspace_define_data_finalizer object, LIBTRUFFLERUBY[:rb_tr_rdata_run_finalizer], rdata, use_cext_lock

    Primitive.cext_mark_object_on_call_exit(object) unless Truffle::Interop.null?(mark)

    object
  end

  def rb_data_typed_object_wrap(ruby_class, data, data_type, mark, free, size)
    ruby_class = Object unless ruby_class
    object = ruby_class.__send__(:__layout_allocate__)
    use_cext_lock = Primitive.use_cext_lock?

    rtypeddata = LIBTRUFFLERUBY.rb_tr_rtypeddata_create(data_type, data)
    Primitive.object_hidden_var_set object, DATA_STRUCT, rtypeddata
    Primitive.object_hidden_var_set object, DATA_MARKER, data_marker(LIBTRUFFLERUBY[:rb_tr_rtypeddata_run_marker], rtypeddata)
    # Could use a simpler finalizer if Truffle::Interop.null?(free)
    Primitive.objectspace_define_data_finalizer object, LIBTRUFFLERUBY[:rb_tr_rtypeddata_run_finalizer], rtypeddata, use_cext_lock

    unless Truffle::Interop.null?(size)
      Primitive.object_hidden_var_set object, DATA_MEMSIZER, data_sizer(LIBTRUFFLERUBY[:rb_tr_rtypeddata_run_memsizer], rtypeddata)
    end
    Primitive.object_hidden_var_set object, DATA_TYPE, data_type

    Primitive.cext_mark_object_on_call_exit(object) unless Truffle::Interop.null?(mark)

    object
  end

  def run_data_finalizer(function, data, use_cext_lock)
    Primitive.call_with_cext_lock_and_frame function, [data], nil, nil, use_cext_lock
  end

  def run_marker(obj)
    Primitive.array_mark_store(obj) if Primitive.array_store_native?(obj)

    marker = Primitive.object_hidden_var_get obj, DATA_MARKER
    unless Primitive.nil?(marker)
      create_mark_list(obj)
      marker.call
      set_mark_list_on_object(obj)
    end
  end

  def rb_ruby_verbose_ptr
    $VERBOSE
  end

  def rb_ruby_debug_ptr
    $DEBUG
  end

  def rb_tr_not_implemented(function_name)
    raise NotImplementedError, "The C API function #{function_name} is not implemented yet on TruffleRuby"
  end

  def rb_f_notimplement
    function = caller(1, 1)
    raise NotImplementedError, "#{function}() function is unimplemented on this machine"
  end

  def rb_bug(message)
    raise Exception, "rb_bug: #{message}"
  end

  def rb_fatal(message)
    raise Exception, "rb_fatal: #{message}"
  end

  def test_kwargs(kwargs, raise_error)
    return false if Primitive.nil?(kwargs)

    if Primitive.is_a?(kwargs, Hash) && kwargs.keys.all? { |k| Primitive.is_a?(k, Symbol) }
      true
    elsif raise_error
      raise ArgumentError, "the value is not a Hash with all keys being Symbols as kwargs requires: #{kwargs}"
    else
      false
    end
  end

  def send_splatted(object, method, args)
    object.__send__(method, *args)
  end

  def rb_block_call(object, method, args, func, data)
    use_cext_lock = Primitive.use_cext_lock?

    object.__send__(method, *args) do |*block_args|
      Primitive.cext_unwrap(Primitive.call_with_cext_lock(RB_BLOCK_CALL_FUNC_WRAPPER, [ # Probably need to save the frame here for blocks.
        func,
        Primitive.cext_wrap(block_args.first),
        data,
        block_args.size, # argc
        Truffle::CExt.RARRAY_PTR(block_args), # argv
        nil, # blockarg
      ], use_cext_lock))
    end
  end

  def rb_module_new
    Module.new
  end

  def rb_ensure(b_proc, data1, e_proc, data2)
    begin
      Primitive.interop_execute(POINTER_TO_POINTER_WRAPPER, [b_proc, data1])
    ensure
      errinfo = Primitive.fiber_get_error_info
      Primitive.fiber_set_error_info($!)
      begin
        Primitive.interop_execute(POINTER_TO_POINTER_WRAPPER, [e_proc, data2])
      ensure
        Primitive.fiber_set_error_info(errinfo)
      end
    end
  end
  Primitive.always_split self, :rb_ensure

  def rb_rescue(b_proc, data1, r_proc, data2)
    begin
      Primitive.interop_execute(POINTER_TO_POINTER_WRAPPER, [b_proc, data1])
    rescue StandardError => exc
      if Truffle::Interop.null?(r_proc)
        Primitive.cext_wrap(nil)
      else
        errinfo = Primitive.fiber_get_error_info
        Primitive.fiber_set_error_info(exc)
        begin
          Primitive.interop_execute(POINTER2_TO_POINTER_WRAPPER, [r_proc, data2, Primitive.cext_wrap(exc)])
        ensure
          Primitive.fiber_set_error_info(errinfo)
        end
      end
    end
  end
  Primitive.always_split self, :rb_rescue

  def rb_rescue2(b_proc, data1, r_proc, data2, rescued)
    begin
      Primitive.interop_execute(POINTER_TO_POINTER_WRAPPER, [b_proc, data1])
    rescue *rescued => exc
      errinfo = Primitive.fiber_get_error_info
      Primitive.fiber_set_error_info(exc)
      begin
        Primitive.interop_execute(POINTER2_TO_POINTER_WRAPPER, [r_proc, data2, Primitive.cext_wrap(exc)])
      ensure
        Primitive.fiber_set_error_info(errinfo)
      end
    end
  end
  Primitive.always_split self, :rb_rescue2

  def rb_exec_recursive(func, obj, arg)
    result = nil

    recursive = Truffle::ThreadOperations.detect_recursion(obj) do
      result = Primitive.cext_unwrap(Primitive.interop_execute(
        POINTER2_INT_TO_POINTER_WRAPPER,
        [func, Primitive.cext_wrap(obj), Primitive.cext_wrap(arg), 0]))
    end

    if recursive
      Primitive.cext_unwrap(Primitive.interop_execute(
        POINTER2_INT_TO_POINTER_WRAPPER,
        [func, Primitive.cext_wrap(obj), Primitive.cext_wrap(arg), 1]))
    else
      result
    end
  end
  Primitive.always_split self, :rb_exec_recursive

  def rb_catch_obj(tag, func, data)
    use_cext_lock = Primitive.use_cext_lock?

    catch tag do |caught|
      Primitive.cext_unwrap(Primitive.call_with_cext_lock(RB_BLOCK_CALL_FUNC_WRAPPER, [
        func,
        Primitive.cext_wrap(caught),
        Primitive.cext_wrap(data),
        0, # argc
        nil, # argv
        nil, # blockarg
      ], use_cext_lock))
    end
  end

  def rb_memerror
    raise NoMemoryError, 'failed to allocate memory'
  end

  def rb_struct_define_no_splat(name, attrs)
    Struct.new(name, *attrs)
  end

  def rb_struct_define_under_no_splat(outer, name, attrs)
    struct = Struct.new(nil, *attrs)
    rb_const_set outer, name, struct
    struct
  end

  def rb_struct_aref(struct, index)
    struct[index]
  end

  def rb_struct_aset(struct, index, value)
    struct[index] = value
  end

  def rb_struct_getmember(struct, member)
    struct[member]
  end

  def rb_struct_size(klass)
    klass.members.size
  end

  def rb_struct_new_no_splat(klass, args)
    klass.new(*args)
  end

  def rb_struct_initialize(struct_or_data, values)
    unless Primitive.is_a?(struct_or_data, Struct) || Primitive.is_a?(struct_or_data, Data)
      raise ArgumentError, 'the first argument must be a Struct or Data instance'
    end
    raise ArgumentError, 'expected values to be an Array' unless Primitive.is_a?(values, Array)
    Primitive.check_frozen(struct_or_data)

    members = Primitive.class(struct_or_data).members
    raise ArgumentError, 'struct size differs' if values.size > members.size

    members.zip(values) do |member, value|
      Primitive.object_hidden_var_set struct_or_data, member, value
    end

    if Primitive.is_a?(struct_or_data, Data)
      struct_or_data.freeze
    end

    nil
  end

  def rb_data_define_no_splat(klass, attrs)
    klass ||= Data
    Truffle::Type.rb_check_type(klass, Class)

    unless klass <= Data
      raise TypeError, 'invalid Class for rb_data_define(), expected Data or a subclass of Data'
    end

    klass.define(*attrs)
  end

  def yield_no_block
    raise LocalJumpError
  end

  def rb_time_nano_new(sec, nsec)
    ORIGINAL_TIME_AT.call(sec, nsec, :nanosecond)
  end

  def rb_time_timespec_new(sec, nsec, offset, is_utc, is_local)
    if is_local
      ORIGINAL_TIME_AT.call(sec, nsec, :nanosecond)
    elsif is_utc
      ORIGINAL_TIME_AT.call(sec, nsec, :nanosecond, in: 0).getgm
    else
      ORIGINAL_TIME_AT.call(sec, nsec, :nanosecond, in: offset)
    end
  end

  def rb_time_num_new(timev, off)
    ORIGINAL_TIME_AT.call(timev).getlocal(off)
  end

  def rb_time_interval_acceptable(time_val)
    # TODO (pitr-ch 09-Mar-2017): more precise error messages
    raise TypeError, 'cannot be Time' if Primitive.is_a?(time_val, Time)
    raise ArgumentError, 'cannot be negative' if time_val < 0
  end

  def rb_thread_create(fn, args)
    use_cext_lock = Primitive.use_cext_lock?
    Thread.new do
      Primitive.call_with_cext_lock_and_frame(POINTER_TO_POINTER_WRAPPER, [fn, args], Primitive.caller_special_variables_if_available, nil, use_cext_lock)
    end
  end

  def rb_thread_call_with_gvl(function, data)
    Primitive.call_with_cext_lock(POINTER_TO_POINTER_WRAPPER, [function, data], CEXT_LOCK)
  end

  def rb_thread_call_without_gvl(function, data1, unblock, data2)
    Primitive.send_without_cext_lock(
      self,
      :rb_thread_call_without_gvl_inner,
      [function, data1, unblock, data2],
      nil)
  end

  private def rb_thread_call_without_gvl_inner(function, data1, unblock, data2)
    if SULONG
      Truffle::Interop.to_native(unblock)
      Truffle::Interop.to_native(data2)
    end

    Primitive.call_with_unblocking_function(Thread.current,
      POINTER_TO_POINTER_WRAPPER, function, data1,
      Truffle::Interop.as_pointer(unblock), Truffle::Interop.as_pointer(data2))
  end

  def rb_iterate(iteration, iterated_object, callback, callback_arg)
    use_cext_lock = Primitive.use_cext_lock?
    block = rb_block_proc
    wrapped_callback = proc do |block_arg|
      Primitive.call_with_cext_lock_and_frame_and_unwrap(RB_BLOCK_CALL_FUNC_WRAPPER, [
        callback,
        Primitive.cext_wrap(block_arg),
        Primitive.cext_wrap(callback_arg),
        0, # argc
        nil, # argv
        nil, # blockarg
      ], Primitive.cext_special_variables_from_stack, block, use_cext_lock)
    end
    Primitive.cext_unwrap(
      Primitive.call_with_cext_lock_and_frame(POINTER_TO_POINTER_WRAPPER, [
        iteration,
        Primitive.cext_wrap(iterated_object)
      ], Primitive.cext_special_variables_from_stack, wrapped_callback, use_cext_lock))
  end

  # From ruby.h
  RB_WAITFD_IN = Truffle::IOOperations::POLLIN
  RB_WAITFD_PRI = Truffle::IOOperations::POLLPRI
  RB_WAITFD_OUT = Truffle::IOOperations::POLLOUT

  def rb_thread_wait_fd(fd)
    io = IO.for_fd(fd)
    io.autoclose = false
    Primitive.send_without_cext_lock(Truffle::IOOperations, :poll, [io, RB_WAITFD_IN, nil], nil)
    RB_WAITFD_IN
  end

  def rb_thread_fd_writable(fd)
    io = IO.for_fd(fd)
    io.autoclose = false
    Primitive.send_without_cext_lock(Truffle::IOOperations, :poll, [io, RB_WAITFD_OUT, nil], nil)
    RB_WAITFD_OUT
  end

  def rb_wait_for_single_fd(fd, events, tv_secs, tv_usecs)
    timeout = nil
    if tv_secs >= 0 || tv_usecs >= 0
      timeout = tv_secs + tv_usecs/1.0e6
    end

    io = IO.for_fd(fd)
    io.autoclose = false
    returned_events = Primitive.send_without_cext_lock(Truffle::IOOperations, :poll, [io, events, timeout], nil)
    returned_events & (RB_WAITFD_IN | RB_WAITFD_OUT | RB_WAITFD_PRI)
  end

  def rb_call_super(args)
    rb_call_super_splatted(*args)
  end

  def rb_any_to_s(object)
    Primitive.rb_any_to_s(object)
  end

  def rb_obj_as_string(object)
    Truffle::Type.rb_obj_as_string(object)
  end

  def rb_class_inherited_p(ruby_module, object)
    if Primitive.is_a?(object, Module)
      ruby_module <= object
    else
      raise TypeError
    end
  end

  def rb_get_special_vars
    vars = Primitive.cext_special_variables_from_stack
    unless vars
      vars = Truffle::ThreadOperations.ruby_caller_special_variables([Truffle::CExt, Truffle::CExt.singleton_class, Truffle::Interop.singleton_class])
    end
    vars
  end

  def rb_backref_get
    Primitive.regexp_last_match_get(rb_get_special_vars())
  end

  def rb_backref_set(value)
    Truffle::RegexpOperations::LAST_MATCH_SET.call(nil, value, rb_get_special_vars())
  end

  def rb_gv_set(name, value)
    binding.eval("#{name} = value")
  end

  def rb_gv_get(name)
    name = "$#{name}" unless name.start_with?('$')
    if name == '$~'
      rb_backref_get
    else
      Primitive.rb_gv_get(Primitive.to_java_string(name))
    end
  end

  def rb_reg_match(re, str)
    result = Truffle::RegexpOperations.match(re, str, 0)
    Primitive.regexp_last_match_set(rb_get_special_vars(), result)

    result.begin(0) if result
  end

  def rb_hash_aref(object, key)
    object[key]
  end

  def rb_define_hooked_variable(name, gvar, getter, setter)
    name = "$#{name}" unless name.start_with?('$')
    id = name.to_sym
    use_cext_lock = Primitive.use_cext_lock?

    getter_proc = -> {
      Primitive.call_with_cext_lock_and_frame_and_unwrap(
        POINTER2_TO_POINTER_WRAPPER,
        [getter, Primitive.cext_wrap(id), gvar],
        Primitive.caller_special_variables_if_available,
        nil,
        use_cext_lock)
    }

    setter_proc = -> _, value {
      Primitive.call_with_cext_lock_and_frame(
        POINTER3_TO_VOID_WRAPPER,
        [setter, Primitive.cext_wrap(value), Primitive.cext_wrap(id), gvar],
        Primitive.caller_special_variables_if_available,
        nil,
        use_cext_lock)
    }

    Truffle::KernelOperations.define_hooked_variable id, getter_proc, setter_proc
  end

  LOG_WARNING = Truffle::Boot.get_option 'cexts-log-warnings'

  def rb_tr_log_warning(message)
    Truffle::Debug.log_warning message if LOG_WARNING
  end

  def rb_convert_to_encoding(encoding)
    if Primitive.is_a?(encoding, Encoding)
      encoding
    else
      Encoding.find(encoding.to_str)
    end
  end

  def rb_to_encoding(encoding)
    RbEncoding.get(encoding)
  end

  def rb_enc_from_encoding(rb_encoding)
    rb_encoding.encoding
  end

  def rb_enc_from_native_encoding(rb_encoding)
    RbEncoding.get_encoding_from_native(rb_encoding)
  end

  def native_string?(string)
    Primitive.string_is_native?(string)
  end

  def RSTRING_PTR(string)
    Primitive.string_pointer_to_native(string)
  end

  def rb_java_class_of(object)
    Truffle::Debug.java_class_of(object)
  end

  def rb_java_to_string(object)
    Truffle::Interop.to_string(object)
  end

  def rb_sprintf(format, *args)
    # TODO (kjmenard 19-May-17) We shouldn't just ignore the '+' modifier. This is a hack to just get things running, even if it produces bad data.
    f = format.gsub(/%(?:\+)?l.\v/, '%s')

    # TODO (kjmenard 30-Oct-17) Deal with this hack better. Ruby's `sprintf` doesn't understand the '%l' modifier. But Ruby also doesn't delineate between 32-bit and 64-bit integers, so stripping out the modifier should be fine.
    f = f.gsub('%ld', '%d')

    sprintf(f, *args) rescue raise ArgumentError, "Bad format string #{f}."
  end

  def test_cext_wrap(value)
    Primitive.cext_wrap(value)
  end

  # An object that once unreachable frees the associated native pointer using ruby_xfree()
  class IMemoTmpBufAutoFree
    def pointer=(address)
      ObjectSpace.define_finalizer(self, Primitive.class(self).free(address))
    end

    def self.free(address)
      -> _id { LIBTRUFFLERUBY.ruby_xfree(address) }
    end
  end

  def rb_imemo_tmpbuf_auto_free_pointer
    IMemoTmpBufAutoFree.new
  end

  def rb_imemo_tmpbuf_set_ptr(obj, ptr)
    obj.pointer = ptr
  end

  RB_THREAD_CURRENT_BACKTRACE_LOCATIONS_OMIT = NFI ? 4 : 5

  def rb_debug_inspector_open_contexts_and_backtrace
    contexts = Truffle::Debug.get_frame_bindings.map do |binding|
      if binding
        [binding.receiver, Primitive.class(binding.receiver), binding]
      else
        [nil, nil, nil]
      end
    end.freeze

    backtrace = Thread.current.backtrace_locations(RB_THREAD_CURRENT_BACKTRACE_LOCATIONS_OMIT)

    if contexts.size != backtrace.size
      STDERR.puts '', 'contexts:', contexts.map { |_,_,binding| binding.source_location.join(':') }
      STDERR.puts '', 'backtrace:', backtrace, ''
      raise 'debug_inspector contexts and backtrace lengths are not equal'
    end

    [contexts, backtrace]
  end

  def rb_syserr_new(errno, mesg)
    SystemCallError.new(mesg, errno)
  end

  def rb_lastline_set(str)
    Primitive.io_last_line_set(rb_get_special_vars(), str)
  end

  def rb_lastline_get
    Primitive.io_last_line_get(rb_get_special_vars())
  end

  def rb_cFiber
    Fiber
  end

  def rb_fiber_current
    Fiber.current
  end

  def rb_fiber_new(function, value)
    use_cext_lock = Primitive.use_cext_lock?
    Fiber.new do |*args|
      Primitive.call_with_cext_lock_and_frame_and_unwrap(RB_BLOCK_CALL_FUNC_WRAPPER, [
        function,
        Primitive.cext_wrap(args.first), # yieldarg
        nil, # procarg,
        0, # argc
        nil, # argv
        nil, # blockarg
      ], nil, nil, use_cext_lock)
    end
  end

  def rb_tr_pointer(pointer)
    Truffle::Interop.to_native(pointer) unless Truffle::Interop.pointer?(pointer)
    raise "#{pointer} not a pointer" unless Truffle::Interop.pointer?(pointer)
    Truffle::FFI::Pointer.new(pointer)
  end

  def rb_exc_set_message(e, mesg)
    Primitive.exception_set_message(e, mesg)
  end

  # This could also be an Array similar to the global_list in MRI.
  # But that makes it more difficult to test the case the global is in native memory,
  # and would be slower for rb_gc_unregister_address().
  GC_REGISTERED_ADDRESSES = {}

  def rb_gc_register_address(address)
    # Make it a native pointer so its identity hash is stable
    Truffle::Interop.to_native(address) unless Truffle::Interop.pointer?(address)

    c_global_variables = Primitive.fiber_c_global_variables
    if c_global_variables # called during Init_ function
      c_global_variables << address
    else
      # Read it immediately if outside Init_ function.
      # This assumes the value is already set when this is called and does not change after that.
      register_address(address)
    end
  end

  def resolve_registered_addresses
    Primitive.fiber_c_global_variables.each { |address| register_address(address) }
  end

  private def register_address(address)
    # We save the ValueWrapper here and not the actual value/object, this is important for primitives like double and
    # not-fixnum-long, as we need to preserve the handle by preserving the ValueWrapper of that handle.
    # For those cases the primitive cannot itself reference its ValueWrapper, unlike RubyDynamicObject and ImmutableRubyObject.
    GC_REGISTERED_ADDRESSES[address] = Primitive.cext_to_wrapper LIBTRUFFLERUBY.rb_tr_read_VALUE_pointer(address)
  end

  def rb_gc_unregister_address(address)
    Truffle::Interop.to_native(address) unless Truffle::Interop.pointer?(address)
    GC_REGISTERED_ADDRESSES.delete(address)
  end

  def rb_eval_cmd_kw(cmd, args, kw_splat)
    if (args.size > 0 && kw_splat != 0)
      kwargs = args.pop
      cmd.call(*args, **kwargs)
    else
      cmd.call(*args)
    end
  end

  def rb_error_frozen_object(object)
    string = nil
    recursion = Truffle::ThreadOperations.detect_recursion object do
      string = object.inspect
    end

    if recursion
      string = ' ...'
    end

    raise FrozenError.new("can't modify frozen #{Primitive.class(object)}: #{string}", receiver: object)
  end

  def rb_tr_warn(message)
    location = caller_locations(1, 1)[0]
    message_with_prefix = "#{location.label}: warning: #{message}"
    Warning.warn(message_with_prefix)
  end

  def rb_warning_category_enabled_p(category)
    Warning[category]
  end

  def rb_tr_warn_category(message, category)
    location = caller_locations(1, 1)[0]
    message_with_prefix = "#{location.label}: warning: #{message}"
    Warning.warn(message_with_prefix, category: category)
  end

  def rb_tr_flags(object)
    Truffle::CExt::RBasic.new(object).compute_flags
  end

  def rb_tr_set_flags(object, flags)
    Truffle::CExt::RBasic.new(object).set_flags(flags)
  end

  def rb_io_get_write_io(io)
    if Primitive.is_a?(io, IO::BidirectionalPipe)
      Primitive.object_ivar_get(io, :@write)
    else
      io
    end
  end

  def new_memory_pointer(size)
    Truffle::FFI::MemoryPointer.new(size)
  end

  def rb_io_mode(io)
    io.instance_variable_get(:@mode)
  end

  def rb_io_path(io)
    io.instance_variable_get(:@path)
  end

  def rb_io_open_descriptor(klass, fd, mode, path, timeout, internal_encoding, external_encoding, flags, options)
    # Translate platform-specific file open flags (`O_`) to corresponding Ruby-specific modes (`FMODE_`).
    # Ruby interface accepts `FMODE_` flags, but C API functions accept `O_` flags.
    mode = Truffle::IOOperations.translate_omode_to_fmode(mode)

    klass.new(fd, mode, **options, internal_encoding: internal_encoding, external_encoding: external_encoding, path: path, flags: flags)
  end

  def rb_tr_io_pointer(io)
    Primitive.object_hidden_var_get(io, RB_IO_STRUCT)
  end

  def rb_tr_io_attach_pointer(io, rb_io_t)
    unless Primitive.is_a?(rb_io_t, Truffle::FFI::MemoryPointer)
      raise 'The rb_io_t must be a MemoryPointer to keep it alive as long as IO object'
    end
    Primitive.object_hidden_var_set(io, RB_IO_STRUCT, rb_io_t)
  end

  def rb_enc_alias(alias_name, original_name)
    enc = Encoding.find(original_name)
    Truffle::EncodingOperations.define_alias(enc, alias_name)
    enc
  end
end
