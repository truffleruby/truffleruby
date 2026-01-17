/*
 * Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>
#include <ruby/debug.h>

struct rb_debug_inspector_struct {
    VALUE backtrace;
    VALUE contexts; /* [[self, klass, binding], ...] */
    long backtrace_size;
};

VALUE rb_debug_inspector_backtrace_locations(const rb_debug_inspector_t *dc) {
  return dc->backtrace;
}

VALUE rb_debug_inspector_open(rb_debug_inspector_func_t func, void *data) {
  rb_debug_inspector_t dbg_context;
  VALUE contexts_and_backtrace = RUBY_CEXT_INVOKE("rb_debug_inspector_open_contexts_and_backtrace");
  VALUE contexts = RARRAY_AREF(contexts_and_backtrace, 0);
  VALUE backtrace = RARRAY_AREF(contexts_and_backtrace, 1);
  dbg_context.contexts = contexts;
  dbg_context.backtrace = backtrace;
  dbg_context.backtrace_size = RARRAY_LEN(backtrace);
  return (*func)(&dbg_context, data);
}

static VALUE frame_get(const rb_debug_inspector_t *dc, long index) {
  if (index < 0 || index >= dc->backtrace_size) {
    rb_raise(rb_eArgError, "no such frame");
  }
  return rb_ary_entry(dc->contexts, index);
}

enum {
  CALLER_BINDING_SELF,
  CALLER_BINDING_CLASS,
  CALLER_BINDING_BINDING
};

VALUE rb_debug_inspector_frame_binding_get(const rb_debug_inspector_t *dc, long index) {
  VALUE frame = frame_get(dc, index);
  return rb_ary_entry(frame, CALLER_BINDING_BINDING);
}

VALUE rb_debug_inspector_frame_iseq_get(const rb_debug_inspector_t *dc, long index) {
  return Qnil;
}

VALUE rb_debug_inspector_frame_class_get(const rb_debug_inspector_t *dc, long index) {
  VALUE frame = frame_get(dc, index);
  return rb_ary_entry(frame, CALLER_BINDING_CLASS);
}

VALUE rb_debug_inspector_frame_self_get(const rb_debug_inspector_t *dc, long index) {
  VALUE frame = frame_get(dc, index);
  return rb_ary_entry(frame, CALLER_BINDING_SELF);
}
