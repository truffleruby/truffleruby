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

// Structs, rb_struct_*

VALUE rb_struct_aref(VALUE s, VALUE idx) {
  return RUBY_CEXT_INVOKE("rb_struct_aref", s, idx);
}

VALUE rb_struct_aset(VALUE s, VALUE idx, VALUE val) {
  return RUBY_CEXT_INVOKE("rb_struct_aset", s, idx, val);
}

VALUE rb_struct_getmember(VALUE s, ID member) {
  return RUBY_CEXT_INVOKE("rb_struct_getmember", s, ID2SYM(member));
}

VALUE rb_tr_struct_define_va_list(const char *name, va_list args) {
  VALUE rb_name = name == NULL ? Qnil : rb_str_new_cstr(name);
  VALUE ary = rb_ary_new();
  int i = 0;
  char *arg;
  while ((arg = va_arg(args, char*)) != NULL) {
    rb_ary_push(ary, rb_str_new_cstr(arg));
    i++;
  }
  return RUBY_CEXT_INVOKE("rb_struct_define_no_splat", rb_name, ary);
}

VALUE rb_tr_struct_define_under_va_list(VALUE space, const char *name, va_list args) {
  VALUE rb_name = rb_str_new_cstr(name);
  VALUE ary = rb_ary_new();
  int i = 0;
  char *arg;
  while ((arg = va_arg(args, char*)) != NULL) {
    rb_ary_push(ary, rb_str_new_cstr(arg));
    i++;
  }
  return RUBY_CEXT_INVOKE("rb_struct_define_under_no_splat", space, rb_name, ary);
}

VALUE rb_tr_struct_new_va_list(VALUE klass, va_list args) {
  int members = polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_struct_size", klass));
  VALUE ary = rb_ary_new_capa(members);
  int i = 0;
  while (i < members) {
    VALUE arg = va_arg(args, VALUE);
    rb_ary_push(ary, arg);
    i++;
  }
  return RUBY_CEXT_INVOKE("rb_struct_new_no_splat", klass, ary);
}

VALUE rb_struct_s_members(VALUE klass) {
  return RUBY_INVOKE(klass, "members");
}

VALUE rb_struct_members(VALUE s) {
  return RUBY_INVOKE(s, "members");
}

VALUE rb_struct_size(VALUE s) {
  return RUBY_INVOKE(s, "size");
}

VALUE rb_tr_data_define_va_list(VALUE super, va_list args) {
  VALUE ary = rb_ary_new();
  int i = 0;
  char *arg;
  while ((arg = va_arg(args, char*)) != NULL) {
    rb_ary_push(ary, rb_str_new_cstr(arg));
    i++;
  }
  return RUBY_CEXT_INVOKE("rb_data_define_no_splat", super, ary);
}

VALUE rb_struct_initialize(VALUE self, VALUE values) {
  return RUBY_CEXT_INVOKE("rb_struct_initialize", self, values);
}
