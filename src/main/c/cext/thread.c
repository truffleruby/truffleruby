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
#include <ruby/thread_native.h>

// Threads, rb_thread_*, rb_nativethread_*

int rb_thread_alone(void) {
  return rb_tr_up_rb_thread_alone();
}

VALUE rb_thread_current(void) {
  return rb_tr_up_send0_current(rb_cThread);
}

VALUE rb_thread_local_aref(VALUE thread, ID id) {
  return rb_tr_up_send1_aref(thread, ID2SYM(id));
}

VALUE rb_thread_local_aset(VALUE thread, ID id, VALUE val) {
  return rb_tr_up_send2_aset(thread, ID2SYM(id), val);
}

void rb_tr_thread_wait_for(struct timeval* time) {
  double seconds = (double)time->tv_sec + (double)time->tv_usec/1000000;
  rb_tr_up_send1_o_sleep(rb_mKernel, DBL2NUM(seconds));
}

void rb_thread_check_ints(void) {
  rb_tr_up_rb_thread_check_ints();
}

int rb_thread_check_trap_pending(void) {
  return 0;
}

VALUE rb_thread_wakeup(VALUE thread) {
  return rb_tr_up_send0_wakeup(thread);
}

VALUE rb_thread_create(VALUE (*fn)(void *g), void *arg) {
  return rb_tr_up_rb_thread_create(fn, arg);
}

void rb_thread_schedule(void) {
  rb_tr_up_send0_o_pass(rb_cThread);
}

rb_nativethread_id_t rb_nativethread_self(void) {
  return rb_tr_up_rb_nativethread_self();
}

void rb_nativethread_lock_initialize(rb_nativethread_lock_t *lock) {
  *lock = rb_tr_up_rb_nativethread_lock_initialize();
}

void rb_nativethread_lock_destroy(rb_nativethread_lock_t *lock) {
  *lock = rb_tr_up_rb_nativethread_lock_destroy(*lock);
}

void rb_nativethread_lock_lock(rb_nativethread_lock_t *lock) {
  rb_tr_up_send0_o_lock(*lock);
}

void rb_nativethread_lock_unlock(rb_nativethread_lock_t *lock) {
  rb_tr_up_send0_o_unlock(*lock);
}

int ruby_native_thread_p(void) {
  return rb_tr_up_ruby_native_thread_p();
}

int ruby_thread_has_gvl_p(void) {
  return rb_tr_up_rb_tr_cext_lock_owned_p() ? 1 : 0;
}
