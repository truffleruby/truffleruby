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

// Global variables, rb_gvar_*, rb_gv_*

VALUE rb_gvar_val_getter(ID id, VALUE *data) {
  return (VALUE) data;
}

VALUE rb_gvar_var_getter(ID id, VALUE *var) {
  if (!var) {
    return Qnil;
  }

  return *var;
}

void rb_gvar_var_setter(VALUE val, ID id, VALUE *data) {
  *data = val;
}

void rb_define_hooked_variable(const char *name, VALUE *var, rb_gvar_getter_t *getter, rb_gvar_setter_t *setter) {
  if (!getter) {
    getter = rb_gvar_var_getter;
  }

  if (!setter) {
    setter = rb_gvar_var_setter;
  }

  polyglot_invoke(RUBY_CEXT, "rb_define_hooked_variable", rb_tr_unwrap(rb_str_new_cstr(name)), var, getter, setter);
}

void rb_gvar_readonly_setter(VALUE val, ID id, VALUE *data) {
  rb_raise(rb_eNameError, "read-only variable");
}

void rb_define_readonly_variable(const char *name, const VALUE *var) {
  rb_define_hooked_variable(name, (VALUE *)var, NULL, rb_gvar_readonly_setter);
}

void rb_define_variable(const char *name, VALUE *var) {
  rb_define_hooked_variable(name, var, 0, 0);
}

void rb_define_virtual_variable(const char *name, rb_gvar_getter_t *getter, rb_gvar_setter_t *setter) {
    if (!getter) {
      getter = rb_gvar_val_getter;
    }

    if (!setter) {
      setter = rb_gvar_readonly_setter;
    }

    rb_define_hooked_variable(name, 0, getter, setter);
}

VALUE rb_f_global_variables(void) {
  return RUBY_CEXT_INVOKE("rb_f_global_variables");
}

VALUE rb_gv_set(const char *name, VALUE value) {
  return RUBY_CEXT_INVOKE("rb_gv_set", rb_str_new_cstr(name), value);
}

VALUE rb_gv_get(const char *name) {
  return RUBY_CEXT_INVOKE("rb_gv_get", rb_str_new_cstr(name));
}

// $VERBOSE

VALUE rb_tr_ruby_verbose_ptr;

VALUE *rb_ruby_verbose_ptr(void) {
  rb_tr_ruby_verbose_ptr = RUBY_CEXT_INVOKE("rb_ruby_verbose_ptr");
  return &rb_tr_ruby_verbose_ptr;
}

// $DEBUG

VALUE rb_tr_ruby_debug_ptr;

VALUE *rb_ruby_debug_ptr(void) {
  rb_tr_ruby_debug_ptr = RUBY_CEXT_INVOKE("rb_ruby_debug_ptr");
  return &rb_tr_ruby_debug_ptr;
}

// These refer Ruby global variables and their value can change,
// so we use macros instead of C global variables like MRI, which would be complicated to update.

VALUE rb_tr_stdin(void) {
  return RUBY_CEXT_INVOKE("rb_stdin");
}

VALUE rb_tr_stdout(void) {
  return RUBY_CEXT_INVOKE("rb_stdout");
}

VALUE rb_tr_stderr(void) {
  return RUBY_CEXT_INVOKE("rb_stderr");
}

VALUE rb_tr_fs(void) {
  return RUBY_CEXT_INVOKE("rb_fs");
}

VALUE rb_tr_output_fs(void) {
  return RUBY_CEXT_INVOKE("rb_output_fs");
}

VALUE rb_tr_rs(void) {
  return RUBY_CEXT_INVOKE("rb_rs");
}

VALUE rb_tr_output_rs(void) {
  return RUBY_CEXT_INVOKE("rb_output_rs");
}

VALUE rb_tr_default_rs(void) {
  return RUBY_CEXT_INVOKE("rb_default_rs");
}
