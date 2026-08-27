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
#include <ruby.h>
#include <ruby/encoding.h>
#include <ruby/vm.h>

#include <stdlib.h>
#include <stdarg.h>
#include <stdbool.h>

#include <internal_all.h>

#include <upcalls.h>

// Private helper macros

#define rb_boolean(c) ((c) ? Qtrue : Qfalse)

#define rb_binary_encoding() rb_ascii8bit_encoding()

#define MIN(a,b) (((a)<(b))?(a):(b))
#define MAX(a,b) (((a)>(b))?(a):(b))

// Private functions

extern void rb_tr_set_default_alloc_func(VALUE klass, rb_alloc_func_t func);
extern VALUE rb_tr_default_alloc_func(VALUE klass);

// Create a native MutableTruffleString from ptr and len without copying.
// The returned RubyString is only valid as long as ptr is valid (typically only as long as the caller is on the stack),
// so this must be only used as an argument to an internal Truffle::CExt method which does not return or store
// the RubyString but only run some operation on it.
VALUE rb_tr_temporary_native_string(const char *ptr, long len, rb_encoding *enc);

VALUE rb_tr_static_native_string(const char *ptr, long len, rb_encoding *enc);
