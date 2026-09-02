# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle::CExt
  # Methods defined in this file are not considered as Ruby code implementing MRI C parts,
  # see org.truffleruby.cext.CExtNodes.BlockProcNode

  # The Primitive.cext_invoke_* call for each arity, spelled out literally so that
  # tool/find_unused_primitives.rb finds these primitives used
  INVOKE_PRIMITIVES = {
    -2 => 'Primitive.cext_invoke_l_lll',  # (VALUE obj, VALUE rubyArrayArgs)
    -1 => 'Primitive.cext_invoke_l_lill', # (int argc, VALUE *argv, VALUE obj)
    0 => 'Primitive.cext_invoke_l_ll',
    1 => 'Primitive.cext_invoke_l_lll',
    2 => 'Primitive.cext_invoke_l_llll',
    3 => 'Primitive.cext_invoke_l_lllll',
    4 => 'Primitive.cext_invoke_l_llllll',
    5 => 'Primitive.cext_invoke_l_lllllll',
    6 => 'Primitive.cext_invoke_l_llllllll',
    7 => 'Primitive.cext_invoke_l_lllllllll',
    8 => 'Primitive.cext_invoke_l_llllllllll',
    9 => 'Primitive.cext_invoke_l_lllllllllll',
    10 => 'Primitive.cext_invoke_l_llllllllllll',
    11 => 'Primitive.cext_invoke_l_lllllllllllll',
    12 => 'Primitive.cext_invoke_l_llllllllllllll',
    13 => 'Primitive.cext_invoke_l_lllllllllllllll',
    14 => 'Primitive.cext_invoke_l_llllllllllllllll',
    15 => 'Primitive.cext_invoke_l_lllllllllllllllll',
  }.freeze

  # The method body source per arity: a fixed-arity lambda so no splat/arity checks are needed at
  # run time, calling the setjmp wrapper of that arity with the matching fixed-signature
  # Primitive.cext_invoke_* directly in the lambda frame, so there is no extra Ruby frame between
  # the method body and the native call (rb_frame_this_func() and rb_call_super() walk the stack).
  # It is eval'ed with the binding of rb_define_method below, capturing the wrapper, function and
  # use_cext_lock locals; each method definition gets its own copy of the AST.
  # We must pass the block argument so that the `rb_block_*` functions will be able to find it by
  # walking the stack.
  METHOD_BODY_TEMPLATES = (-2..15).to_h do |argc|
    call_args =
      case argc
      when -1 # (int argc, VALUE *argv, VALUE obj)
        'function, args.size, Truffle::CExt.RARRAY_PTR(args), Primitive.cext_wrap(self)'
      when -2 # (VALUE obj, VALUE rubyArrayArgs)
        'function, Primitive.cext_wrap(self), Primitive.cext_wrap(args)'
      else # (VALUE obj); (VALUE obj, VALUE arg1); (VALUE obj, VALUE arg1, VALUE arg2); ...
        "function, Primitive.cext_wrap(self)#{(1..argc).map { |i| ", Primitive.cext_wrap(arg#{i})" }.join}"
      end
    params = argc >= 0 ? (1..argc).map { |i| "arg#{i}, " }.join : '*args, '
    [argc, <<~RUBY]
      # truffleruby_primitives: true
      ->(#{params}&block) do
        locked = Primitive.cext_push_lock_and_frame(Primitive.caller_special_variables_if_available, block, _use_cext_lock)
        begin
          Primitive.cext_unwrap(#{INVOKE_PRIMITIVES.fetch(argc)}(_wrapper, #{call_args}))
        ensure
          Primitive.cext_pop_lock_and_frame(locked)
        end
      end
    RUBY
  end

  # methods defined with rb_define_method are normal Ruby methods, therefore they cannot be defined in the cext.rb
  # file because blocks passed as arguments would be skipped by org.truffleruby.cext.CExtNodes.BlockProcNode
  def rb_define_method(mod, name, function, argc)
    if argc < -2 or 15 < argc
      raise ArgumentError, "arity out of range: #{argc} for -2..15"
    end

    # Underscore-prefixed as they are only used by the eval'ed method body, to avoid unused variable warnings
    _wrapper = RB_DEFINE_METHOD_WRAPPERS[argc]
    _use_cext_lock = Primitive.use_cext_lock?
    method_body = Truffle::Graal.copy_captured_locals eval(METHOD_BODY_TEMPLATES[argc], binding, __FILE__, __LINE__)

    # Even if the argc is -2, the arity number
    # is still any number of arguments, -1
    arity = argc == -2 ? -1 : argc

    method_body_with_arity = Primitive.proc_specify_arity(method_body, arity)
    Primitive.proc_set_identity(method_body_with_arity, function)
    mod.define_method(name, method_body_with_arity)
  end
end
