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

// Instance variables, rb_iv_*, rb_ivar_*

VALUE rb_obj_instance_variables(VALUE object) {
  return rb_tr_up_rb_obj_instance_variables(object);
}

#undef rb_iv_get
VALUE rb_iv_get(VALUE object, const char *name) {
  return rb_tr_up_rb_ivar_get(object, ID2SYM(rb_intern(name)));
}

#undef rb_iv_set
VALUE rb_iv_set(VALUE object, const char *name, VALUE value) {
  rb_tr_up_rb_ivar_set(object, ID2SYM(rb_intern(name)), value);
  return value;
}

VALUE rb_ivar_defined(VALUE object, ID id) {
  return rb_tr_up_rb_ivar_defined(object, ID2SYM(id));
}

st_index_t rb_ivar_count(VALUE object) {
  return NUM2ULONG(rb_tr_up_rb_ivar_count(object));
}

VALUE rb_ivar_get(VALUE object, ID name) {
  return rb_tr_up_rb_ivar_get(object, ID2SYM(name));
}

VALUE rb_ivar_set(VALUE object, ID name, VALUE value) {
  rb_tr_up_rb_ivar_set(object, ID2SYM(name), value);
  return value;
}

VALUE rb_ivar_lookup(VALUE object, const char *name, VALUE default_value) {
  return rb_tr_up_rb_ivar_lookup(object, rb_str_new_cstr(name), default_value);
}

// Needed to gem install oj
void rb_ivar_foreach(VALUE obj, int (*func)(ID name, VALUE val, st_data_t arg), st_data_t arg) {
  rb_tr_up_rb_ivar_foreach(obj, func, (void*)arg);
}

VALUE rb_attr_get(VALUE object, ID name) {
  return rb_tr_up_rb_ivar_lookup(object, ID2SYM(name), Qnil);
}

void rb_copy_generic_ivar(VALUE clone, VALUE obj) {
  rb_tr_up_rb_copy_generic_ivar(clone, obj);
}

void rb_free_generic_ivar(VALUE obj) {
  rb_tr_up_rb_free_generic_ivar(obj);
}
