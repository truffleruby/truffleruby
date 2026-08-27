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

// GC, rb_gc_*

VALUE rb_tr_gc_guard(VALUE value) {
  rb_tr_up_rb_tr_gc_guard(value);
  return value;
}

void rb_global_variable(VALUE *address) {
  rb_gc_register_address(address);
}

void rb_gc_register_address(VALUE *address) {
  /* NOTE: this captures the value after the Init_ function returns and assumes the value does not change after that. */
  rb_tr_up_rb_gc_register_address(address);
}

void rb_gc_unregister_address(VALUE *address) {
  rb_tr_up_rb_gc_unregister_address(address);
}

void rb_gc_mark(VALUE ptr) {
  rb_tr_up_rb_gc_mark(ptr);
}

void rb_gc_mark_locations(const VALUE *start, const VALUE *end) {
  const VALUE *value = start;

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
    rb_tr_up_rb_gc_mark(ptr);
  }
}

VALUE rb_gc_enable(void) {
  return rb_tr_up_rb_gc_enable();
}

VALUE rb_gc_disable(void) {
  return rb_tr_up_rb_gc_disable();
}

void rb_gc(void) {
  rb_tr_up_rb_gc();
}

VALUE rb_gc_latest_gc_info(VALUE key) {
  return rb_tr_up_rb_gc_latest_gc_info(key);
}

void rb_gc_adjust_memory_usage(ssize_t diff) {
  // No-op for now
  (void) diff; // To silence -Wunused-parameter
}

void rb_gc_register_mark_object(VALUE obj) {
  // No rb_tr_unwrap() here as the caller actually wants a ValueWrapper or a handle
  rb_tr_up_rb_gc_register_mark_object(obj);
}

void* rb_tr_read_VALUE_pointer(VALUE *pointer) {
  // No rb_tr_unwrap() here as the caller actually wants a ValueWrapper or a handle
  return *pointer;
}

int rb_during_gc(void) {
  return 0;
}
