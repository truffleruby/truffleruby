/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2020-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>

// Defining classes, modules and methods, rb_define_*

VALUE rb_f_notimplement(int argc, const VALUE *argv, VALUE obj, VALUE marker) {
  RUBY_CEXT_INVOKE("rb_f_notimplement");
  UNREACHABLE;
}

VALUE rb_define_class(const char *name, VALUE superclass) {
  return rb_define_class_under(rb_cObject, name, superclass);
}

VALUE rb_define_class_under(VALUE module, const char *name, VALUE superclass) {
  return rb_define_class_id_under(module, rb_intern(name), superclass);
}

VALUE rb_define_class_id_under(VALUE module, ID name, VALUE superclass) {
  return RUBY_CEXT_INVOKE("rb_define_class_under", module, ID2SYM(name), superclass);
}

VALUE rb_define_module(const char *name) {
  return rb_define_module_under(rb_cObject, name);
}

VALUE rb_define_module_under(VALUE module, const char *name) {
  return RUBY_CEXT_INVOKE("rb_define_module_under", module, rb_str_new_cstr(name));
}

void rb_include_module(VALUE module, VALUE to_include) {
  RUBY_INVOKE_NO_WRAP(module, "include", to_include);
}

#undef rb_define_method
void rb_define_method(VALUE module, const char *name, VALUE (*function)(ANYARGS), int argc) {
  if (function == rb_tr_rb_f_notimplement) {
    RUBY_CEXT_INVOKE("rb_define_method_undefined", module, rb_str_new_cstr(name));
  } else {
    rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_define_method", rb_tr_unwrap(module), rb_tr_unwrap(rb_str_new_cstr(name)), function, argc));
  }
}

#undef rb_define_private_method
void rb_define_private_method(VALUE module, const char *name, VALUE (*function)(ANYARGS), int argc) {
  rb_define_method(module, name, function, argc);
  RUBY_INVOKE_NO_WRAP(module, "private", rb_str_new_cstr(name));
}

#undef rb_define_protected_method
void rb_define_protected_method(VALUE module, const char *name, VALUE (*function)(ANYARGS), int argc) {
  rb_define_method(module, name, function, argc);
  RUBY_INVOKE_NO_WRAP(module, "protected", rb_str_new_cstr(name));
}

#undef rb_define_module_function
void rb_define_module_function(VALUE module, const char *name, VALUE (*function)(ANYARGS), int argc) {
  rb_define_method(module, name, function, argc);
  polyglot_invoke(RUBY_CEXT, "cext_module_function", rb_tr_unwrap(module), rb_tr_id2sym(rb_intern(name)));
}

#undef rb_define_global_function
void rb_define_global_function(const char *name, VALUE (*function)(ANYARGS), int argc) {
  rb_define_module_function(rb_mKernel, name, function, argc);
}

#undef rb_define_singleton_method
void rb_define_singleton_method(VALUE object, const char *name, VALUE (*function)(ANYARGS), int argc) {
  rb_define_method(rb_singleton_class(object), name, function, argc);
}

void rb_define_alias(VALUE module, const char *new_name, const char *old_name) {
  rb_alias(module, rb_intern(new_name), rb_intern(old_name));
}

void rb_alias(VALUE module, ID new_name, ID old_name) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_alias", module, ID2SYM(new_name), ID2SYM(old_name));
}

void rb_undef_method(VALUE module, const char *name) {
  rb_undef(module, rb_intern(name));
}

void rb_undef(VALUE module, ID name) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_undef", module, ID2SYM(name));
}

void rb_attr(VALUE ruby_class, ID name, int read, int write, int ex) {
  polyglot_invoke(RUBY_CEXT, "rb_attr", rb_tr_unwrap(ruby_class), rb_tr_unwrap(ID2SYM(name)), read, write, ex);
}

void rb_define_alloc_func(VALUE ruby_class, rb_alloc_func_t alloc_function) {
  polyglot_invoke(RUBY_CEXT, "rb_define_alloc_func", rb_tr_unwrap(ruby_class), alloc_function);
}

void rb_undef_alloc_func(VALUE ruby_class) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_undef_alloc_func", ruby_class);
}

rb_alloc_func_t rb_get_alloc_func(VALUE klass) {
  return RUBY_CEXT_INVOKE_NO_WRAP("rb_get_alloc_func", klass);
}

void rb_tr_set_default_alloc_func(VALUE ruby_class, rb_alloc_func_t alloc_function) {
  polyglot_invoke(RUBY_CEXT, "rb_tr_set_default_alloc_func", rb_tr_unwrap(ruby_class), alloc_function);
}

VALUE rb_tr_default_alloc_func(VALUE klass) {
  return RUBY_CEXT_INVOKE("rb_tr_default_alloc_func", klass);
}

VALUE rb_define_class_id(ID id, VALUE super) {
  // id is deliberately ignored - see MRI
  if (!super) {
    super = rb_cObject;
  }
  return rb_class_new(super);
}

VALUE rb_define_module_id(ID id) {
  // id is deliberately ignored - see MRI
  return rb_module_new();
}
