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

// Memory-related function, *alloc*, *free*, rb_mem*

void ruby_malloc_size_overflow(size_t count, size_t elsize) {
  rb_raise(rb_eArgError,
     "malloc: possible integer overflow (%"PRIdSIZE"*%"PRIdSIZE")",
     count, elsize);
}

static size_t xmalloc2_size(const size_t count, const size_t elsize) {
  size_t ret;
  if (rb_mul_size_overflow(count, elsize, SSIZE_MAX, &ret)) {
    ruby_malloc_size_overflow(count, elsize);
  }
  return ret;
}

void *ruby_xmalloc(size_t size) {
  void* result = malloc(size);
  if (result == NULL && size) {
    rb_memerror();
  }
  return result;
}

void *ruby_xmalloc2(size_t n, size_t size) {
  size_t total_size = xmalloc2_size(n, size);
  if (total_size == 0) {
    total_size = 1;
  }
  return ruby_xmalloc(total_size);
}

void *ruby_xcalloc(size_t n, size_t size) {
  size_t total_size = xmalloc2_size(n, size);
  void* result = calloc(1, total_size);
  if (result == NULL && total_size) {
    rb_memerror();
  }
  return result;
}

void *ruby_xrealloc(void *ptr, size_t new_size) {
  void* result = realloc(ptr, new_size);
  if (result == NULL && new_size) {
    rb_memerror();
  }
  return result;
}

void *ruby_xrealloc2(void *ptr, size_t n, size_t size) {
  size_t total_size = xmalloc2_size(n, size);
  return ruby_xrealloc(ptr, total_size);
}

void ruby_xfree(void *address) {
  free(address);
}

void *rb_alloc_tmp_buffer(volatile VALUE *store, long len) {
  if (len == 0) {
    len = 1;
  }
  void *ptr = ruby_xmalloc(len);
  *((void**)store) = ptr;
  return ptr;
}

void *rb_alloc_tmp_buffer_with_count(volatile VALUE *store, size_t size, size_t cnt) {
  return rb_alloc_tmp_buffer(store, size);
}

void rb_free_tmp_buffer(volatile VALUE *store) {
  ruby_xfree(*((void**)store));
}

void rb_mem_clear(VALUE *mem, long n) {
  for (int i = 0; i < n; i++) {
    mem[i] = Qnil;
  }
}
