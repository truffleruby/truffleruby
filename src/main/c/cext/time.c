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

// Time, rb_time_*

VALUE rb_time_new(time_t sec, long usec) {
  return rb_tr_wrap(polyglot_invoke(rb_tr_unwrap(rb_cTime), "at", sec, usec));
}

VALUE rb_time_nano_new(time_t sec, long nsec) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_time_nano_new", sec, nsec));
}

VALUE rb_time_num_new(VALUE timev, VALUE off) {
  return RUBY_CEXT_INVOKE("rb_time_num_new", timev, off);
}

void rb_tr_time_interval(VALUE time_val, struct timeval *result) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_time_interval_acceptable", time_val);

  VALUE time = rb_time_num_new(time_val, Qnil);
  result->tv_sec = polyglot_as_i64(RUBY_INVOKE_NO_WRAP(time, "tv_sec"));
  result->tv_usec = polyglot_as_i64(RUBY_INVOKE_NO_WRAP(time, "tv_usec"));
}

void rb_tr_time_timeval(VALUE time_val, struct timeval *result) {
  VALUE time = rb_time_num_new(time_val, Qnil);
  result->tv_sec = polyglot_as_i64(RUBY_INVOKE_NO_WRAP(time, "tv_sec"));
  result->tv_usec = polyglot_as_i64(RUBY_INVOKE_NO_WRAP(time, "tv_usec"));
}

void rb_tr_time_timespec(VALUE time_val, struct timespec *result) {
  VALUE time = rb_time_num_new(time_val, Qnil);
  result->tv_sec = polyglot_as_i64(RUBY_INVOKE_NO_WRAP(time, "tv_sec"));
  result->tv_nsec = polyglot_as_i64(RUBY_INVOKE_NO_WRAP(time, "tv_nsec"));
}

VALUE rb_time_timespec_new(const struct timespec *ts, int offset) {
  void* is_utc = rb_tr_unwrap(rb_boolean(offset == INT_MAX-1));
  void* is_local = rb_tr_unwrap(rb_boolean(offset == INT_MAX));
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_time_timespec_new",
    ts->tv_sec,
    ts->tv_nsec,
    offset,
    is_utc,
    is_local));
}

void rb_timespec_now(struct timespec *ts) {
  struct timeval tv;
  VALUE time = RUBY_INVOKE(rb_cTime, "now");
  rb_tr_time_timeval(time, &tv);
  ts->tv_sec = tv.tv_sec;
  ts->tv_nsec = tv.tv_usec * 1000;
}
