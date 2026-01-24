/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>

// Calling Ruby methods and blocks from C

RBIMPL_ATTR_NORETURN()
void rb_error_arity(int argc, int min, int max) {
  rb_exc_raise(rb_exc_new3(rb_eArgError, rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_arity_error_string", argc, min, max))));
}

VALUE rb_iterate(VALUE (*function)(VALUE), VALUE arg1, rb_block_call_func_t block, VALUE arg2) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_iterate", function, rb_tr_unwrap(arg1), block, rb_tr_unwrap(arg2)));
}

int rb_respond_to(VALUE object, ID name) {
  return RUBY_CEXT_INVOKE("rb_respond_to", object, ID2SYM(name));
}

VALUE rb_funcallv(VALUE object, ID name, int args_count, const VALUE *args) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_funcallv",
    rb_tr_unwrap(object),
    rb_tr_unwrap(ID2SYM(name)),
    polyglot_from_VALUE_array(args, args_count)));
}

VALUE rb_funcallv_kw(VALUE object, ID name, int args_count, const VALUE *args, int kw_splat) {
  if (kw_splat && args_count > 0) {
    return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_funcallv_keywords",
      rb_tr_unwrap(object),
      rb_tr_unwrap(ID2SYM(name)),
      polyglot_from_VALUE_array(args, args_count)));
  } else {
    return rb_funcallv(object, name, args_count, args);
  }
}

VALUE rb_funcallv_public(VALUE object, ID name, int args_count, const VALUE *args) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_funcallv_public",
    rb_tr_unwrap(object),
    rb_tr_unwrap(ID2SYM(name)),
    polyglot_from_VALUE_array(args, args_count)));
}

VALUE rb_check_funcall(VALUE object, ID name, int args_count, const VALUE *args) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_check_funcall",
    rb_tr_unwrap(object),
    rb_tr_unwrap(ID2SYM(name)),
    rb_tr_unwrap(rb_ary_new_from_values(args_count, args))));
}

VALUE rb_apply(VALUE object, ID name, VALUE args) {
  return RUBY_CEXT_INVOKE("rb_apply", object, ID2SYM(name), args);
}

VALUE rb_block_call(VALUE object, ID name, int args_count, const VALUE *args, rb_block_call_func_t block_call_func, VALUE data) {
  if (rb_block_given_p()) {
    return rb_funcall_with_block(object, name, args_count, args, rb_block_proc());
  } else if (block_call_func == NULL) {
    return rb_funcallv(object, name, args_count, args);
  } else {
    return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_block_call", rb_tr_unwrap(object), rb_tr_unwrap(ID2SYM(name)), rb_tr_unwrap(rb_ary_new4(args_count, args)), block_call_func, (void*)data));
  }
}

VALUE rb_each(VALUE array) {
  if (rb_block_given_p()) {
    return rb_funcall_with_block(array, rb_intern("each"), 0, NULL, rb_block_proc());
  } else {
    return RUBY_INVOKE(array, "each");
  }
}

VALUE rb_call_super(int args_count, const VALUE *args) {
  return RUBY_CEXT_INVOKE("rb_call_super", rb_ary_new4(args_count, args));
}

int rb_keyword_given_p(void) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("rb_keyword_given_p")) ? RB_PASS_KEYWORDS : RB_NO_KEYWORDS;
}

int rb_block_given_p(void) {
  return !NIL_P(rb_block_proc());
}

VALUE rb_block_proc(void) {
  return RUBY_CEXT_INVOKE("rb_block_proc");
}

VALUE rb_block_lambda(void) {
  return rb_block_proc();
}

VALUE rb_yield(VALUE value) {
  if (rb_block_given_p()) {
    return RUBY_CEXT_INVOKE("rb_yield", value);
  } else {
    return RUBY_CEXT_INVOKE("yield_no_block");
  }
}

VALUE rb_funcall_with_block(VALUE recv, ID mid, int argc, const VALUE *argv, VALUE pass_procval) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_funcall_with_block",
    rb_tr_unwrap(recv),
    rb_tr_unwrap(ID2SYM(mid)),
    polyglot_from_VALUE_array(argv, argc),
    rb_tr_unwrap(pass_procval)));
}

VALUE rb_funcall_with_block_kw(VALUE recv, ID mid, int argc, const VALUE *argv, VALUE procval, int kw_splat) {
  if (kw_splat && argc > 0) {
    return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_funcall_with_block_keywords",
      rb_tr_unwrap(recv),
      rb_tr_unwrap(ID2SYM(mid)),
      polyglot_from_VALUE_array(argv, argc),
      rb_tr_unwrap(procval)));
  } else {
    return rb_funcall_with_block(recv, mid, argc, argv, procval);
  }
}

VALUE rb_yield_splat(VALUE values) {
  if (rb_block_given_p()) {
    return RUBY_CEXT_INVOKE("rb_yield_splat", values);
  } else {
    return RUBY_CEXT_INVOKE("yield_no_block");
  }
}

VALUE rb_tr_ary_new_from_args_va_list(long n, va_list args);

VALUE rb_tr_yield_values_va_list(int n, va_list args) {
  VALUE values = rb_tr_ary_new_from_args_va_list(n, args);
  return rb_yield_splat(values);
}

VALUE rb_yield_values2(int n, const VALUE *argv) {
  VALUE values = rb_ary_new_capa(n);
  for (int i = 0; i < n; i++) {
    rb_ary_store(values, i, (VALUE) argv[i]);
  }
  return rb_yield_splat(values);
}

void *rb_thread_call_with_gvl(gvl_call *function, void *data1) {
  return polyglot_invoke(RUBY_CEXT, "rb_thread_call_with_gvl", function, data1);
}

void* rb_thread_call_without_gvl(gvl_call *function, void *data1, rb_unblock_function_t *unblock_function, void *data2) {
  if (unblock_function == RUBY_UBF_IO) {
    unblock_function = (rb_unblock_function_t*) NULL;
  }

  return polyglot_invoke(RUBY_CEXT, "rb_thread_call_without_gvl", function, data1, unblock_function, data2);
}

void* rb_thread_call_without_gvl2(gvl_call *function, void *data1, rb_unblock_function_t *unblock_function, void *data2) {
  return rb_thread_call_without_gvl(function, data1, unblock_function, data2);
}

void* rb_nogvl(gvl_call *function, void *data1, rb_unblock_function_t *unblock_function, void *data2, int flags) {
  return rb_thread_call_without_gvl(function, data1, unblock_function, data2);
}

ID rb_frame_this_func(void) {
  return SYM2ID(RUBY_CEXT_INVOKE("rb_frame_this_func"));
}

void rb_need_block(void) {
  if (!rb_block_given_p()) {
    rb_raise(rb_eLocalJumpError, "no block given");
  }
}

void rb_iter_break(void) {
  rb_iter_break_value(Qnil);
}

void rb_iter_break_value(VALUE value) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_iter_break_value", value);
  UNREACHABLE;
}

const char *rb_sourcefile(void) {
  return RSTRING_PTR(RUBY_CEXT_INVOKE("rb_sourcefile"));
}

int rb_sourceline(void) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_sourceline"));
}

int rb_obj_method_arity(VALUE object, ID id) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_obj_method_arity", object, ID2SYM(id)));
}

VALUE rb_obj_method(VALUE object, VALUE id) {
  return RUBY_CEXT_INVOKE("rb_obj_method", object, id);
}

int rb_obj_respond_to(VALUE object, ID id, int priv) {
  return polyglot_as_boolean(polyglot_invoke(RUBY_CEXT, "rb_obj_respond_to", rb_tr_unwrap(object), rb_tr_id2sym(id), priv));
}

int rb_method_boundp(VALUE klass, ID id, int ex) {
  return polyglot_as_i32(polyglot_invoke(RUBY_CEXT, "rb_method_boundp", rb_tr_unwrap(klass), rb_tr_id2sym(id), ex));
}

VALUE rb_exec_recursive(VALUE (*func) (VALUE, VALUE, int), VALUE obj, VALUE arg) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_exec_recursive", func, rb_tr_unwrap(obj), rb_tr_unwrap(arg)));
}

VALUE rb_eval_cmd_kw(VALUE cmd, VALUE args, int kw_splat) {
  if (!RB_TYPE_P(cmd, T_STRING)) {
    return RUBY_CEXT_INVOKE("rb_eval_cmd_kw", cmd, args, INT2NUM(kw_splat));
  } else {
    return RUBY_CEXT_INVOKE("rb_eval_string", cmd);
  }
}
