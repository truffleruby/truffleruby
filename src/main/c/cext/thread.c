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
#include <ruby/thread_native.h>

// Threads, rb_thread_*, rb_nativethread_*

int rb_thread_alone(void) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_thread_alone"));
}

VALUE rb_thread_current(void) {
  return RUBY_INVOKE(rb_cThread, "current");
}

VALUE rb_thread_local_aref(VALUE thread, ID id) {
  return RUBY_INVOKE(thread, "[]", ID2SYM(id));
}

VALUE rb_thread_local_aset(VALUE thread, ID id, VALUE val) {
  return RUBY_INVOKE(thread, "[]=", ID2SYM(id), val);
}

void rb_tr_thread_wait_for(struct timeval* time) {
  double seconds = (double)time->tv_sec + (double)time->tv_usec/1000000;
  polyglot_invoke(rb_tr_unwrap(rb_mKernel), "sleep", seconds);
}

void rb_thread_check_ints(void) {
  RUBY_CEXT_INVOKE_NO_WRAP("rb_thread_check_ints");
}

int rb_thread_check_trap_pending(void) {
  return 0;
}

VALUE rb_thread_wakeup(VALUE thread) {
  return RUBY_INVOKE(thread, "wakeup");
}

VALUE rb_thread_create(VALUE (*fn)(void *g), void *arg) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_thread_create", fn, arg));
}

void rb_thread_schedule(void) {
  RUBY_INVOKE_NO_WRAP(rb_cThread, "pass");
}

rb_nativethread_id_t rb_nativethread_self(void) {
  return RUBY_CEXT_INVOKE("rb_nativethread_self");
}

void rb_nativethread_lock_initialize(rb_nativethread_lock_t *lock) {
  *lock = RUBY_CEXT_INVOKE("rb_nativethread_lock_initialize");
}

void rb_nativethread_lock_destroy(rb_nativethread_lock_t *lock) {
  *lock = RUBY_CEXT_INVOKE("rb_nativethread_lock_destroy", *lock);
}

void rb_nativethread_lock_lock(rb_nativethread_lock_t *lock) {
  RUBY_INVOKE_NO_WRAP(*lock, "lock");
}

void rb_nativethread_lock_unlock(rb_nativethread_lock_t *lock) {
  RUBY_INVOKE_NO_WRAP(*lock, "unlock");
}

int ruby_native_thread_p(void) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("ruby_native_thread_p"));
}
