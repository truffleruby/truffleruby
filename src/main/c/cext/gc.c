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
#include <internal/gc.h>

// GC, rb_gc_*

VALUE rb_tr_gc_guard(VALUE value) {
  polyglot_invoke(RUBY_CEXT, "rb_tr_gc_guard", value);
  return value;
}

void rb_global_variable(VALUE *address) {
  rb_gc_register_address(address);
}

void rb_gc_register_address(VALUE *address) {
  /* NOTE: this captures the value after the Init_ function returns and assumes the value does not change after that. */
  polyglot_invoke(RUBY_CEXT, "rb_gc_register_address", address);
}

void rb_gc_unregister_address(VALUE *address) {
  polyglot_invoke(RUBY_CEXT, "rb_gc_unregister_address", address);
}

void rb_gc_mark(VALUE ptr) {
  polyglot_invoke(RUBY_CEXT, "rb_gc_mark", ptr);
}

void rb_gc_mark_locations(const VALUE *start, const VALUE *end) {
  VALUE *value = start;

  while (value < end) {
    rb_gc_mark_maybe(*value);
    value++;
  }
}

void rb_gc_mark_movable(VALUE obj) {
  rb_gc_mark(obj);
}

void rb_gc_mark_maybe(VALUE ptr) {
  if (!RB_TYPE_P(ptr, T_NONE)) {
    polyglot_invoke(RUBY_CEXT, "rb_gc_mark", ptr);
  }
}

VALUE rb_gc_enable(void) {
  return RUBY_CEXT_INVOKE("rb_gc_enable");
}

VALUE rb_gc_disable(void) {
  return RUBY_CEXT_INVOKE("rb_gc_disable");
}

void rb_gc(void) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_gc");
}

VALUE rb_gc_latest_gc_info(VALUE key) {
  return RUBY_CEXT_INVOKE("rb_gc_latest_gc_info", key);
}

void rb_gc_adjust_memory_usage(ssize_t diff) {
  // No-op for now
  (void) diff; // To silence -Wunused-parameter
}

void rb_gc_register_mark_object(VALUE obj) {
  // No rb_tr_unwrap() here as the caller actually wants a ValueWrapper or a handle
  polyglot_invoke(RUBY_CEXT, "rb_gc_register_mark_object", obj);
}

void* rb_tr_read_VALUE_pointer(VALUE *pointer) {
  // No rb_tr_unwrap() here as the caller actually wants a ValueWrapper or a handle
  return *pointer;
}

int rb_during_gc(void) {
  return 0;
}
