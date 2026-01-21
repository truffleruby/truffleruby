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

// Hash, rb_hash_*

VALUE rb_Hash(VALUE obj) {
  return RUBY_CEXT_INVOKE("rb_Hash", obj);
}

VALUE rb_hash(VALUE obj) {
  return RUBY_CEXT_INVOKE("rb_hash", obj);
}

VALUE rb_hash_new(void) {
  return RUBY_CEXT_INVOKE("rb_hash_new");
}

VALUE rb_hash_new_capa(long capacity) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_hash_new_capa", capacity));
}

VALUE rb_ident_hash_new(void) {
  return RUBY_CEXT_INVOKE("rb_ident_hash_new");
}

VALUE rb_hash_aref(VALUE hash, VALUE key) {
  return RUBY_CEXT_INVOKE("rb_hash_aref", hash, key);
}

VALUE rb_hash_fetch(VALUE hash, VALUE key) {
  return RUBY_INVOKE(hash, "fetch", key);
}

VALUE rb_hash_aset(VALUE hash, VALUE key, VALUE value) {
  return RUBY_INVOKE(hash, "[]=", key, value);
}

VALUE rb_hash_dup(VALUE hash) {
  return rb_obj_dup(hash);
}

VALUE rb_hash_lookup(VALUE hash, VALUE key) {
  return rb_hash_lookup2(hash, key, Qnil);
}

VALUE rb_hash_lookup2(VALUE hash, VALUE key, VALUE default_value) {
  VALUE result = RUBY_CEXT_INVOKE("rb_hash_get_or_undefined", hash, key);
  if (result == Qundef) {
    result = default_value;
  }
  return result;
}

VALUE rb_hash_set_ifnone(VALUE hash, VALUE if_none) {
  return RUBY_CEXT_INVOKE("rb_hash_set_ifnone", hash, if_none);
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
  return RUBY_INVOKE(hash, "clear");
}

VALUE rb_hash_delete(VALUE hash, VALUE key) {
  return RUBY_INVOKE(hash, "delete", key);
}

VALUE rb_hash_delete_if(VALUE hash) {
  if (rb_block_given_p()) {
    return rb_funcall_with_block(hash, rb_intern("delete_if"), 0, NULL, rb_block_proc());
  } else {
    return RUBY_INVOKE(hash, "delete_if");
  }
}

void rb_hash_foreach(VALUE hash, int (*func)(VALUE key, VALUE val, VALUE arg), VALUE arg) {
  polyglot_invoke(RUBY_CEXT, "rb_hash_foreach", rb_tr_unwrap(hash), func, (void*)arg);
}

void rb_hash_bulk_insert(long n, const VALUE *values, VALUE hash) {
  void* unwrapped_hash = rb_tr_unwrap(hash);

  for (long i = 0; i < n; i += 2) {
    polyglot_invoke(unwrapped_hash, "[]=", rb_tr_unwrap(values[i]), rb_tr_unwrap(values[i + 1]));
  }
}

VALUE rb_hash_size(VALUE hash) {
  return RUBY_INVOKE(hash, "size");
}

size_t rb_hash_size_num(VALUE hash) {
  return (size_t) FIX2ULONG(rb_hash_size(hash));
}

st_index_t rb_hash_start(st_index_t h) {
  return (st_index_t) polyglot_as_i64(polyglot_invoke(RUBY_CEXT, "rb_hash_start", h));
}

VALUE rb_hash_freeze(VALUE hash) {
  return rb_obj_freeze(hash);
}
