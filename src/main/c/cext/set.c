/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>

// Set, rb_set_*

VALUE rb_set_new(void) {
  return rb_tr_up_rb_set_new();
}

VALUE rb_set_new_capa(size_t capa) {
  return rb_set_new();
}

bool rb_set_lookup(VALUE set, VALUE element) {
  return RTEST(rb_tr_up_send1_include_p(set, element));
}

bool rb_set_add(VALUE set, VALUE element) {
  return RTEST(rb_tr_up_send1_add_p(set, element));
}

VALUE rb_set_clear(VALUE set) {
  return rb_tr_up_send0_clear(set);
}

bool rb_set_delete(VALUE set, VALUE element) {
  return RTEST(rb_tr_up_send1_delete_p(set, element));
}

size_t rb_set_size(VALUE set) {
  return NUM2SIZET(rb_tr_up_send0_size(set));
}

void rb_set_foreach(VALUE set, int (*func)(VALUE element, VALUE arg), VALUE arg) {
  rb_tr_up_rb_set_foreach(set, func, (void*)arg);
}
