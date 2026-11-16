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

    // Only set for keys created by `rb_ractor_local_storage_value_newkey`, otherwise `Qnil`.
    // It contains an `Array` used as a value wrapper. Once a value has been stored, the stored `VALUE` is the only
    // element of the array; until then, the array is empty. The indirection is a little awkward, but it lets us
    // register the `Array` as a GC root once, when the key is created, and then store values with ordinary `Array`
    // writes. Rooting each stored `VALUE` directly doesn't work: `rb_gc_register_mark_object` can't be undone, so
    // every value ever stored would stay alive, and `rb_gc_register_address` only roots the value present at registration.
    VALUE value_holder;
};

static const struct rb_ractor_local_storage_type ractor_local_storage_type_null = {
  NULL,
  NULL,
};

static const struct rb_ractor_local_storage_type ractor_local_storage_type_value = {
  NULL,
  NULL,
};

static bool ractor_local_storage_value_key_p(rb_ractor_local_key_t key) {
  return key->type == &ractor_local_storage_type_value;
}

static void ractor_local_storage_check_ptr_key(rb_ractor_local_key_t key, const char *function) {
  if (ractor_local_storage_value_key_p(key)) {
    rb_raise(rb_eArgError, "%s() called with a key created by rb_ractor_local_storage_value_newkey()", function);
  }
}

static void ractor_local_storage_check_value_key(rb_ractor_local_key_t key, const char *function) {
  if (!ractor_local_storage_value_key_p(key)) {
    rb_raise(rb_eArgError, "%s() called with a key created by rb_ractor_local_storage_ptr_newkey()", function);
  }
}

rb_ractor_local_key_t rb_ractor_local_storage_ptr_newkey(const struct rb_ractor_local_storage_type *type) {
  rb_ractor_local_key_t key = ALLOC(struct rb_ractor_local_key_struct);
  key->type = type ? type : &ractor_local_storage_type_null;
  key->main_cache = (void *)Qundef;
  key->value_holder = Qnil;
  return key;
}

void *rb_ractor_local_storage_ptr(rb_ractor_local_key_t key) {
  ractor_local_storage_check_ptr_key(key, "rb_ractor_local_storage_ptr");
  if (key->main_cache != (void*)Qundef) {
    return key->main_cache;
  } else {
    return NULL;
  }
}

void rb_ractor_local_storage_ptr_set(rb_ractor_local_key_t key, void *ptr) {
  ractor_local_storage_check_ptr_key(key, "rb_ractor_local_storage_ptr_set");
  key->main_cache = ptr;
}

rb_ractor_local_key_t rb_ractor_local_storage_value_newkey(void) {
  rb_ractor_local_key_t key = rb_ractor_local_storage_ptr_newkey(&ractor_local_storage_type_value);
  key->value_holder = rb_ary_new_capa(1);
  rb_gc_register_mark_object(key->value_holder);
  return key;
}

bool rb_ractor_local_storage_value_lookup(rb_ractor_local_key_t key, VALUE *val) {
  ractor_local_storage_check_value_key(key, "rb_ractor_local_storage_value_lookup");

  // The holder never shrinks, so once it has an element the element stays there.
  if (RARRAY_LEN(key->value_holder) == 0) {
    return false;
  }

  *val = rb_ary_entry(key->value_holder, 0);
  return true;
}

VALUE rb_ractor_local_storage_value(rb_ractor_local_key_t key) {
  ractor_local_storage_check_value_key(key, "rb_ractor_local_storage_value");
  VALUE value;
  return rb_ractor_local_storage_value_lookup(key, &value) ? value : Qnil;
}

void rb_ractor_local_storage_value_set(rb_ractor_local_key_t key, VALUE val) {
  ractor_local_storage_check_value_key(key, "rb_ractor_local_storage_value_set");
  rb_ary_store(key->value_holder, 0, val);
}
