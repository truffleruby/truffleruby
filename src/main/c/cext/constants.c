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

// Accessing Ruby constants, rb_const_*

int rb_const_defined(VALUE module, ID name) {
  return rb_tr_up_rb_const_defined_3f(module, ID2SYM(name));
}

int rb_const_defined_at(VALUE module, ID name) {
  return rb_tr_up_send2_b_const_defined_p(module, ID2SYM(name), Qfalse);
}

VALUE rb_const_get(VALUE module, ID name) {
  return rb_tr_up_rb_const_get(module, ID2SYM(name));
}

VALUE rb_const_get_at(VALUE module, ID name) {
  return rb_tr_up_send2_const_get(module, ID2SYM(name), Qfalse);
}

VALUE rb_const_get_from(VALUE module, ID name) {
  return rb_tr_up_rb_const_get_from(module, ID2SYM(name));
}

VALUE rb_const_remove(VALUE module, ID name) {
  return rb_tr_up_rb_const_remove(module, ID2SYM(name));
}

void rb_const_set(VALUE module, ID name, VALUE value) {
  rb_tr_up_rb_const_set(module, ID2SYM(name), value);
}

void rb_define_const(VALUE module, const char *name, VALUE value) {
  rb_const_set(module, rb_intern(name), value);
}

void rb_define_global_const(const char *name, VALUE value) {
  rb_define_const(rb_cObject, name, value);
}
