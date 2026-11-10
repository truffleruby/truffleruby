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

void rb_tr_setjmp_wrapper_pointer1_to_void(void (*func)(VALUE arg), VALUE arg);

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

/* The VALUEs marked by the mark function currently running on this thread, see rb_tr_run_marker().
 * Marking happens whenever a C extension call accessed the data of a typed or untyped data object
 * (e.g. a Nokogiri::XML::NodeSet marks every node it contains), so marks are collected in C and passed
 * to Java in a single upcall instead of one upcall per marked object. */
struct rb_tr_mark_buffer {
  VALUE *values;
  long count;
  long capacity;
  VALUE inline_values[64];
};

static __thread struct rb_tr_mark_buffer *rb_tr_current_mark_buffer = NULL;

struct rb_tr_marker_call {
  RUBY_DATA_FUNC dmark;
  void *data;
  struct rb_tr_mark_buffer *buffer;
};

static void rb_tr_call_marker(VALUE arg) {
  struct rb_tr_marker_call *call = (struct rb_tr_marker_call *) arg;
  call->dmark(call->data);
  if (call->buffer->count > 0) {
    rb_tr_up_rb_tr_gc_mark_values(call->buffer->values, call->buffer->count);
  }
}

void rb_tr_run_marker(RUBY_DATA_FUNC dmark, void *data) {
  struct rb_tr_mark_buffer buffer;
  buffer.values = buffer.inline_values;
  buffer.count = 0;
  buffer.capacity = sizeof(buffer.inline_values) / sizeof(VALUE);
  struct rb_tr_marker_call call = { dmark, data, &buffer };

  struct rb_tr_mark_buffer *prev_buffer = rb_tr_current_mark_buffer;
  rb_tr_current_mark_buffer = &buffer;
  // If the mark function raises, the marks are dropped and the exception is rethrown when the downcall returns
  rb_tr_setjmp_wrapper_pointer1_to_void(rb_tr_call_marker, (VALUE) &call);
  rb_tr_current_mark_buffer = prev_buffer;

  if (buffer.values != buffer.inline_values) {
    free(buffer.values);
  }
}

// Outside of a mark function there is nothing to mark, like on CRuby outside of GC marking
void rb_gc_mark(VALUE ptr) {
  struct rb_tr_mark_buffer *buffer = rb_tr_current_mark_buffer;
  if (buffer == NULL) {
    return;
  }

  if (UNLIKELY(buffer->count == buffer->capacity)) {
    long capacity = buffer->capacity * 2;
    VALUE *values = malloc(capacity * sizeof(VALUE));
    if (values == NULL) {
      fprintf(stderr, "ERROR: could not allocate the C extension mark buffer.\n");
      abort();
    }
    memcpy(values, buffer->values, buffer->count * sizeof(VALUE));
    if (buffer->values != buffer->inline_values) {
      free(buffer->values);
    }
    buffer->values = values;
    buffer->capacity = capacity;
  }
  buffer->values[buffer->count++] = ptr;
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
    rb_gc_mark(ptr);
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
