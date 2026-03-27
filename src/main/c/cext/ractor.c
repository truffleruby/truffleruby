/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2021-2025 Oracle and/or its affiliates.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
#include <truffleruby-impl.h>
#include <ruby/ractor.h>

// Ractor, rb_ractor_*

void rb_ext_ractor_safe(bool flag) {
  rb_ext_thread_safe(flag);
}

void rb_ext_thread_safe(bool flag) {
  polyglot_invoke(RUBY_CEXT, "set_thread_safe", flag);
}

// Simplified to main Ractor only

VALUE rb_ractor_stdin(void) {
  return rb_stdin;
}

VALUE rb_ractor_stdout(void) {
  return rb_stdout;
}

VALUE rb_ractor_stderr(void) {
  return rb_stderr;
}

// Ractor local storage, simplified to main Ractor only

struct rb_ractor_local_key_struct {
    const struct rb_ractor_local_storage_type *type;
    void *main_cache;
};

static const struct rb_ractor_local_storage_type ractor_local_storage_type_null = {
  NULL,
  NULL,
};

rb_ractor_local_key_t rb_ractor_local_storage_ptr_newkey(const struct rb_ractor_local_storage_type *type) {
  rb_ractor_local_key_t key = ALLOC(struct rb_ractor_local_key_struct);
  key->type = type ? type : &ractor_local_storage_type_null;
  key->main_cache = (void *)Qundef;
  return key;
}

void *rb_ractor_local_storage_ptr(rb_ractor_local_key_t key) {
  if (key->main_cache != (void*)Qundef) {
    return key->main_cache;
  } else {
    return NULL;
  }
}

void rb_ractor_local_storage_ptr_set(rb_ractor_local_key_t key, void *ptr) {
  key->main_cache = ptr;
}
