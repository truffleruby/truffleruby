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

// Hash, rb_hash_*

VALUE rb_Hash(VALUE obj) {
  return rb_tr_up_rb_Hash(obj);
}

VALUE rb_hash(VALUE obj) {
  return rb_tr_up_rb_hash(obj);
}

VALUE rb_hash_new(void) {
  return rb_tr_up_rb_hash_new();
}

VALUE rb_hash_new_capa(long capacity) {
  return rb_tr_up_rb_hash_new_capa(capacity);
}

VALUE rb_ident_hash_new(void) {
  return rb_tr_up_rb_ident_hash_new();
}

VALUE rb_hash_aref(VALUE hash, VALUE key) {
  return rb_tr_up_rb_hash_aref(hash, key);
}

VALUE rb_hash_fetch(VALUE hash, VALUE key) {
  return rb_tr_up_send1_fetch(hash, key);
}

VALUE rb_hash_aset(VALUE hash, VALUE key, VALUE value) {
  return rb_tr_up_send2_aset(hash, key, value);
}

VALUE rb_hash_dup(VALUE hash) {
  return rb_obj_dup(hash);
}

VALUE rb_hash_lookup(VALUE hash, VALUE key) {
  return rb_hash_lookup2(hash, key, Qnil);
}

VALUE rb_hash_lookup2(VALUE hash, VALUE key, VALUE default_value) {
  VALUE result = rb_tr_up_rb_hash_get_or_undefined(hash, key);
  if (result == Qundef) {
    result = default_value;
  }
  return result;
}

VALUE rb_hash_set_ifnone(VALUE hash, VALUE if_none) {
  return rb_tr_up_rb_hash_set_ifnone(hash, if_none);
}

st_index_t rb_memhash(const void *data, long length) {
  // Not a proper hash - just something that produces a stable result for now

  long hash = 0;

  for (long n = 0; n < length; n++) {
    hash = (hash << 1) ^ ((uint8_t*) data)[n];
  }

  return (st_index_t) hash;
}

VALUE rb_hash_clear(VALUE hash) {
  return rb_tr_up_send0_clear(hash);
}

VALUE rb_hash_delete(VALUE hash, VALUE key) {
  return rb_tr_up_send1_delete(hash, key);
}

VALUE rb_hash_delete_if(VALUE hash) {
  if (rb_block_given_p()) {
    return rb_funcall_with_block(hash, rb_intern("delete_if"), 0, NULL, rb_block_proc());
  } else {
    return rb_tr_up_send0_delete_if(hash);
  }
}

void rb_hash_foreach(VALUE hash, int (*func)(VALUE key, VALUE val, VALUE arg), VALUE arg) {
  rb_tr_up_rb_hash_foreach(hash, func, (void*)arg);
}

void rb_hash_bulk_insert(long n, const VALUE *values, VALUE hash) {
  rb_tr_up_rb_hash_bulk_insert(n, values, hash);
}

VALUE rb_hash_size(VALUE hash) {
  return rb_tr_up_send0_size(hash);
}

size_t rb_hash_size_num(VALUE hash) {
  return (size_t) FIX2ULONG(rb_hash_size(hash));
}

st_index_t rb_hash_start(st_index_t h) {
  return (st_index_t) rb_tr_up_rb_hash_start(h);
}

VALUE rb_hash_freeze(VALUE hash) {
  return rb_obj_freeze(hash);
}
