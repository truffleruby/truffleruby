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
  return RUBY_CEXT_INVOKE("rb_set_new");
}

VALUE rb_set_new_capa(size_t capa) {
  return rb_set_new();
}

bool rb_set_lookup(VALUE set, VALUE element) {
  return RTEST(RUBY_INVOKE(set, "include?", element));
}

bool rb_set_add(VALUE set, VALUE element) {
  return RTEST(RUBY_INVOKE(set, "add?", element));
}

VALUE rb_set_clear(VALUE set) {
  return RUBY_INVOKE(set, "clear");
}

bool rb_set_delete(VALUE set, VALUE element) {
  return RTEST(RUBY_INVOKE(set, "delete?", element));
}

size_t rb_set_size(VALUE set) {
  return NUM2SIZET(RUBY_INVOKE(set, "size"));
}

void rb_set_foreach(VALUE set, int (*func)(VALUE element, VALUE arg), VALUE arg) {
  polyglot_invoke(RUBY_CEXT, "rb_set_foreach", rb_tr_unwrap(set), func, (void*)arg);
}
