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

// ensure it for the fiddle gem and its TruffleRuby-specific implementation
RBIMPL_STATIC_ASSERT("sizeof(bool) is 1", sizeof(bool) == 1);

void rb_tr_init_global_constants(const VALUE *constants);

// Run when loading C-extension support. upcalls contains the addresses of the
// FFM upcall stubs in tool/cext-upcalls.rb order, constants the handles of the
// global VALUE constants in tool/generate-cext-constants.rb order.
void rb_tr_init(void **upcalls, const VALUE *constants) {
  rb_tr_init_ffm_upcalls(upcalls);
  rb_tr_init_global_constants(constants);

  // In CRuby some core classes have custom allocation function.
  // So mimic this CRuby implementation detail to satisfy rb_define_alloc_func's specs
  // for classes that are used in these specs only.
  rb_tr_set_default_alloc_func(rb_cBasicObject, rb_tr_default_alloc_func);
  rb_tr_set_default_alloc_func(rb_cArray, rb_tr_default_alloc_func);
  rb_tr_set_default_alloc_func(rb_cString, rb_tr_default_alloc_func);
}
