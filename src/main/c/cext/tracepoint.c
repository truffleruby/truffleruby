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
#include <ruby/debug.h>

// TracePoint, rb_tracepoint_*

VALUE rb_tracepoint_new(VALUE target_thval, rb_event_flag_t events, void (*func)(VALUE, void *), void *data) {
  // target_thval parameter is unused as of MRI 2.6
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_tracepoint_new", events, func, data));
}

VALUE rb_tracepoint_enable(VALUE tpval) {
  return RUBY_INVOKE(tpval, "enable");
}

VALUE rb_tracepoint_disable(VALUE tpval) {
  return RUBY_INVOKE(tpval, "disable");
}

VALUE rb_tracepoint_enabled_p(VALUE tpval) {
  return RUBY_INVOKE(tpval, "enabled?");
}
