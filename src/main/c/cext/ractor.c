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
  rb_tr_up_set_thread_safe(flag);
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
    // Only for keys created by rb_ractor_local_storage_value_newkey(): an Array registered as a GC root, holding the
    // stored VALUE at index 0 once a value was set. Storing the VALUE in native memory would require updating the slot
    // and its GC root together, which is not atomic when the C extension lock is not held (e.g., for extensions calling
    // rb_ext_ractor_safe(true) such as io-console), while an Array store is.
    VALUE value_holder;
};

static const struct rb_ractor_local_storage_type ractor_local_storage_type_null = {
  NULL,
  NULL,
};

static bool ractor_local_storage_value_key_p(rb_ractor_local_key_t key) {
  return key->value_holder != Qnil;
}

rb_ractor_local_key_t rb_ractor_local_storage_ptr_newkey(const struct rb_ractor_local_storage_type *type) {
  rb_ractor_local_key_t key = ALLOC(struct rb_ractor_local_key_struct);
  key->type = type ? type : &ractor_local_storage_type_null;
  key->main_cache = (void *)Qundef;
  key->value_holder = Qnil;
  return key;
}

// Like CRuby, both accessor families share one slot per key, so a VALUE key can be read as a pointer and vice versa.

void *rb_ractor_local_storage_ptr(rb_ractor_local_key_t key) {
  if (ractor_local_storage_value_key_p(key)) {
    VALUE value;
    return rb_ractor_local_storage_value_lookup(key, &value) ? (void *)value : NULL;
  } else if (key->main_cache != (void*)Qundef) {
    return key->main_cache;
  } else {
    return NULL;
  }
}

void rb_ractor_local_storage_ptr_set(rb_ractor_local_key_t key, void *ptr) {
  if (ractor_local_storage_value_key_p(key)) {
    rb_ractor_local_storage_value_set(key, (VALUE)ptr);
  } else {
    key->main_cache = ptr;
  }
}

rb_ractor_local_key_t rb_ractor_local_storage_value_newkey(void) {
  rb_ractor_local_key_t key = rb_ractor_local_storage_ptr_newkey(NULL);
  key->value_holder = rb_ary_new_capa(1);
  rb_gc_register_mark_object(key->value_holder);
  return key;
}

bool rb_ractor_local_storage_value_lookup(rb_ractor_local_key_t key, VALUE *val) {
  if (!ractor_local_storage_value_key_p(key)) {
    if (key->main_cache == (void*)Qundef) {
      return false;
    }
    *val = (VALUE)key->main_cache;
    return true;
  }

  // The holder never shrinks, so once it has an element the element stays there.
  if (RARRAY_LEN(key->value_holder) == 0) {
    return false;
  }

  *val = rb_ary_entry(key->value_holder, 0);
  return true;
}

VALUE rb_ractor_local_storage_value(rb_ractor_local_key_t key) {
  VALUE value;
  return rb_ractor_local_storage_value_lookup(key, &value) ? value : Qnil;
}

void rb_ractor_local_storage_value_set(rb_ractor_local_key_t key, VALUE val) {
  if (ractor_local_storage_value_key_p(key)) {
    rb_ary_store(key->value_holder, 0, val);
  } else {
    key->main_cache = (void *)val;
  }
}
