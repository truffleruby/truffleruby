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

// Non-standard additional functions, rb_tr_*

void rb_tr_not_implemented(const char *function_name) {
  fprintf(stderr, "The C API function %s is not implemented yet on TruffleRuby\n", function_name);
  rb_tr_up_rb_tr_not_implemented(rb_str_new_cstr(function_name));
  UNREACHABLE;
}

void rb_tr_log_warning(const char *message) {
  rb_tr_up_rb_tr_log_warning(rb_str_new_cstr(message));
}

VALUE rb_java_class_of(VALUE obj) {
  return rb_tr_up_rb_java_class_of(obj);
}

VALUE rb_java_to_string(VALUE obj) {
  return rb_tr_up_rb_java_to_string(obj);
}

// BasicObject#equal?
int rb_tr_obj_equal(VALUE first, VALUE second) {
  return RTEST(rb_tr_up_rb_tr_obj_equal(first, second));
}

VALUE rb_tr_zlib_crc_table(void) {
  return rb_tr_up_zlib_get_crc_table();
}

VALUE rb_tr_cext_lock_owned_p(void) {
  return rb_tr_up_rb_tr_cext_lock_owned_p() ? Qtrue : Qfalse;
}

// Used for internal testing
VALUE rb_tr_invoke(VALUE recv, const char* meth) {
  return rb_tr_up_invoke0(recv, meth);
}
