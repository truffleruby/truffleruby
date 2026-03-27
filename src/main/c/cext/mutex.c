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

// Mutex, rb_mutex_*

VALUE rb_mutex_new(void) {
  return RUBY_CEXT_INVOKE("rb_mutex_new");
}

VALUE rb_mutex_locked_p(VALUE mutex) {
  return RUBY_CEXT_INVOKE("rb_mutex_locked_p", mutex);
}

VALUE rb_mutex_trylock(VALUE mutex) {
  return RUBY_CEXT_INVOKE("rb_mutex_trylock", mutex);
}

VALUE rb_mutex_lock(VALUE mutex) {
  return RUBY_CEXT_INVOKE("rb_mutex_lock", mutex);
}

VALUE rb_mutex_unlock(VALUE mutex) {
  return RUBY_CEXT_INVOKE("rb_mutex_unlock", mutex);
}

VALUE rb_mutex_sleep(VALUE mutex, VALUE timeout) {
  return RUBY_CEXT_INVOKE("rb_mutex_sleep", mutex, timeout);
}

VALUE rb_mutex_synchronize(VALUE mutex, VALUE (*func)(VALUE arg), VALUE arg) {
  return (VALUE) polyglot_invoke(RUBY_CEXT, "rb_mutex_synchronize", rb_tr_unwrap(mutex), func, arg);
}
