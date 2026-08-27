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

// Time, rb_time_*

VALUE rb_time_new(time_t sec, long usec) {
  return rb_tr_up_send2_at(rb_cTime, LONG2NUM(sec), LONG2NUM(usec));
}

VALUE rb_time_nano_new(time_t sec, long nsec) {
  return rb_tr_up_rb_time_nano_new(sec, nsec);
}

VALUE rb_time_num_new(VALUE timev, VALUE off) {
  return rb_tr_up_rb_time_num_new(timev, off);
}

void rb_tr_time_interval(VALUE time_val, struct timeval *result) {
  rb_tr_up_rb_time_interval_acceptable(time_val);

  VALUE time = rb_time_num_new(time_val, Qnil);
  result->tv_sec = rb_tr_up_send0_l_tv_sec(time);
  result->tv_usec = rb_tr_up_send0_l_tv_usec(time);
}

void rb_tr_time_timeval(VALUE time_val, struct timeval *result) {
  VALUE time = rb_time_num_new(time_val, Qnil);
  result->tv_sec = rb_tr_up_send0_l_tv_sec(time);
  result->tv_usec = rb_tr_up_send0_l_tv_usec(time);
}

void rb_tr_time_timespec(VALUE time_val, struct timespec *result) {
  VALUE time = rb_time_num_new(time_val, Qnil);
  result->tv_sec = rb_tr_up_send0_l_tv_sec(time);
  result->tv_nsec = rb_tr_up_send0_l_tv_nsec(time);
}

VALUE rb_time_timespec_new(const struct timespec *ts, int offset) {
  void* is_utc = rb_tr_unwrap(rb_boolean(offset == INT_MAX-1));
  void* is_local = rb_tr_unwrap(rb_boolean(offset == INT_MAX));
  return rb_tr_up_rb_time_timespec_new(ts->tv_sec, ts->tv_nsec, offset, is_utc, is_local);
}

void rb_timespec_now(struct timespec *ts) {
  struct timeval tv;
  VALUE time = rb_tr_up_send0_now(rb_cTime);
  rb_tr_time_timeval(time, &tv);
  ts->tv_sec = tv.tv_sec;
  ts->tv_nsec = tv.tv_usec * 1000;
}
