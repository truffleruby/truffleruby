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

#include "ruby.h"
#include "rubyspec.h"

#ifdef __cplusplus
extern "C" {
#endif

static VALUE rbasic_spec_finalize_flag(VALUE self) {
  return INT2FIX(RUBY_FL_FINALIZE);
}

static VALUE rbasic_spec_promoted_flag(VALUE self) {
  return INT2FIX(RUBY_FL_PROMOTED);
}

static VALUE rbasic_spec_get_flags(VALUE self, VALUE obj) {
  return INT2FIX(RBASIC_FLAGS(obj));
}

static VALUE rbasic_spec_set_flags(VALUE self, VALUE obj, VALUE flags) {
  RBASIC_SET_FLAGS(obj, FIX2INT(flags));
  return INT2FIX(RBASIC_FLAGS(obj));
}

void Init_truffleruby_rbasic_spec(void) {
  VALUE cls = rb_define_class("CApiTruffleRubyRBasicSpecs", rb_cObject);
  rb_define_method(cls, "finalize_flag", rbasic_spec_finalize_flag, 0);
  rb_define_method(cls, "promoted_flag", rbasic_spec_promoted_flag, 0);
  rb_define_method(cls, "get_flags", rbasic_spec_get_flags, 1);
  rb_define_method(cls, "set_flags", rbasic_spec_set_flags, 2);
}

#ifdef __cplusplus
}
#endif