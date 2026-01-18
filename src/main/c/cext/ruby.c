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

/*
 * Note that some functions are #undef'd just before declaration.
 * This is needed because these functions are declared by MRI as macros in e.g., ruby.h,
 * and so would produce invalid syntax when using the function name for definition.
 */

// Globals

void* rb_tr_cext;
void* (*rb_tr_unwrap)(VALUE obj);
VALUE (*rb_tr_wrap)(void *obj);
VALUE (*rb_tr_longwrap)(long obj);
void* (*rb_tr_id2sym)(ID id);
ID (*rb_tr_sym2id)(VALUE val);
void* (*rb_tr_force_native)(VALUE obj);
VALUE (*rb_tr_rb_f_notimplement)(int argc, const VALUE *argv, VALUE obj, VALUE marker);

void set_rb_tr_rb_f_notimplement(VALUE (*function)(int argc, const VALUE *argv, VALUE obj, VALUE marker)) {
  rb_tr_rb_f_notimplement = function;
}

void rb_tr_init_global_constants(VALUE (*get_constant)(const char*));

static VALUE sulong_get_constant(const char* name) {
  return RUBY_CEXT_INVOKE(name);
}

void rb_tr_init_exception(void);

// ensure it for the fiddle gem and its TruffleRuby-specific implementation
RBIMPL_STATIC_ASSERT("sizeof(bool) is 1", sizeof(bool) == 1);

// Run when loading C-extension support
void rb_tr_init(void *ruby_cext) {
  rb_tr_cext = ruby_cext;
  rb_tr_unwrap = polyglot_invoke(rb_tr_cext, "rb_tr_unwrap_function");
  rb_tr_id2sym = polyglot_invoke(rb_tr_cext, "rb_tr_id2sym_function");
  rb_tr_sym2id = polyglot_invoke(rb_tr_cext, "rb_tr_sym2id_function");
  rb_tr_wrap = polyglot_invoke(rb_tr_cext, "rb_tr_wrap_function");
  rb_tr_longwrap = polyglot_invoke(rb_tr_cext, "rb_tr_wrap_function");
  rb_tr_force_native = polyglot_invoke(rb_tr_cext, "rb_tr_force_native_function");
  set_rb_tr_rb_f_notimplement(rb_f_notimplement); // the Sulong definition of rb_f_notimplement

  polyglot_invoke(rb_tr_cext, "cext_start_new_handle_block");
  rb_tr_init_exception();
  rb_tr_init_global_constants(sulong_get_constant);

  // In CRuby some core classes have custom allocation function.
  // So mimic this CRuby implementation detail to satisfy rb_define_alloc_func's specs
  // for classes that are used in these specs only.
  rb_tr_set_default_alloc_func(rb_cBasicObject, rb_tr_default_alloc_func);
  rb_tr_set_default_alloc_func(rb_cArray, rb_tr_default_alloc_func);
  rb_tr_set_default_alloc_func(rb_cString, rb_tr_default_alloc_func);

  polyglot_invoke(rb_tr_cext, "cext_start_new_handle_block");
}
