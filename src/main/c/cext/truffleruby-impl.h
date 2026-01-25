/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
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

#include <graalvm/llvm/polyglot.h>

// For polyglot_from_VALUE_array()
POLYGLOT_DECLARE_TYPE(VALUE)

// Private helper macros

#define rb_boolean(c) ((c) ? Qtrue : Qfalse)

// Support

extern void* rb_tr_cext;
#define RUBY_CEXT rb_tr_cext

// For debugging

void rb_tr_log_warning(const char *message);
#define rb_tr_debug(args...) polyglot_invoke(RUBY_CEXT, "rb_tr_debug", args)
int rb_tr_obj_equal(VALUE first, VALUE second);
VALUE rb_java_class_of(VALUE val);
VALUE rb_java_to_string(VALUE val);

// Private functions

extern void* (*rb_tr_unwrap)(VALUE obj);
extern VALUE (*rb_tr_wrap)(void *obj);
extern VALUE (*rb_tr_longwrap)(long obj);
extern void* (*rb_tr_id2sym)(ID obj);
extern ID (*rb_tr_sym2id)(VALUE sym);
extern void* (*rb_tr_force_native)(VALUE obj);
extern VALUE (*rb_tr_rb_f_notimplement)(int argc, const VALUE *argv, VALUE obj, VALUE marker);
extern void rb_tr_set_default_alloc_func(VALUE klass, rb_alloc_func_t func);
extern VALUE rb_tr_default_alloc_func(VALUE klass);


// Create a native MutableTruffleString from ptr and len without copying.
// The returned RubyString is only valid as long as ptr is valid (typically only as long as the caller is on the stack),
// so this must be only used as an argument to an internal Truffle::CExt method which does not return or store
// the RubyString but only run some operation on it.
VALUE rb_tr_temporary_native_string(const char *ptr, long len, rb_encoding *enc);

// Invoking ruby methods.

// These macros implement ways to call the methods on Truffle::CExt
// (RUBY_CEXT_INVOKE) and other ruby objects (RUBY_INVOKE) and handle
// all the unwrapping of arguments. They also come in _NO_WRAP
// variants which will not attempt to wrap the result. This is
// important if it is not an actual ruby object being returned as an
// error will be raised when attempting to wrap such objects.

// Internal macros for the implementation
#define RUBY_INVOKE_IMPL_0(recv, name) polyglot_invoke(recv, name)
#define RUBY_INVOKE_IMPL_1(recv, name, V1) polyglot_invoke(recv, name, rb_tr_unwrap(V1))
#define RUBY_INVOKE_IMPL_2(recv, name, V1, V2) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2))
#define RUBY_INVOKE_IMPL_3(recv, name, V1, V2, V3) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3))
#define RUBY_INVOKE_IMPL_4(recv, name, V1, V2, V3, V4) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4))
#define RUBY_INVOKE_IMPL_5(recv, name, V1, V2, V3, V4, V5) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5))
#define RUBY_INVOKE_IMPL_6(recv, name, V1, V2, V3, V4, V5, V6) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6))
#define RUBY_INVOKE_IMPL_7(recv, name, V1, V2, V3, V4, V5, V6, V7) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7))
#define RUBY_INVOKE_IMPL_8(recv, name, V1, V2, V3, V4, V5, V6, V7, V8) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8))
#define RUBY_INVOKE_IMPL_9(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9))
#define RUBY_INVOKE_IMPL_10(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10))
#define RUBY_INVOKE_IMPL_11(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11))
#define RUBY_INVOKE_IMPL_12(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12))
#define RUBY_INVOKE_IMPL_13(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13))
#define RUBY_INVOKE_IMPL_14(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14))
#define RUBY_INVOKE_IMPL_15(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15))
#define RUBY_INVOKE_IMPL_16(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15), rb_tr_unwrap(V16))
#define RUBY_INVOKE_IMPL_17(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15), rb_tr_unwrap(V16), rb_tr_unwrap(V17))
#define RUBY_INVOKE_IMPL_18(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_unwrap(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15), rb_tr_unwrap(V16), rb_tr_unwrap(V17), rb_tr_unwrap(V18))
#define INVOKE_IMPL(RECV, MESG, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, NAME, ...) NAME
#define RUBY_INVOKE_IMPL_NO_WRAP(RECV, NAME, ...) INVOKE_IMPL(RECV, NAME, ##__VA_ARGS__, RUBY_INVOKE_IMPL_18, RUBY_INVOKE_IMPL_17, RUBY_INVOKE_IMPL_16, RUBY_INVOKE_IMPL_15, RUBY_INVOKE_IMPL_14, RUBY_INVOKE_IMPL_13, RUBY_INVOKE_IMPL_12, RUBY_INVOKE_IMPL_11, RUBY_INVOKE_IMPL_10, RUBY_INVOKE_IMPL_9, RUBY_INVOKE_IMPL_8, RUBY_INVOKE_IMPL_7, RUBY_INVOKE_IMPL_6, RUBY_INVOKE_IMPL_5, RUBY_INVOKE_IMPL_4, RUBY_INVOKE_IMPL_3, RUBY_INVOKE_IMPL_2, RUBY_INVOKE_IMPL_1, RUBY_INVOKE_IMPL_0)(RECV, NAME, ##__VA_ARGS__)
#define RUBY_INVOKE_IMPL(RECV, NAME, ...) rb_tr_wrap(RUBY_INVOKE_IMPL_NO_WRAP(RECV, NAME, ##__VA_ARGS__))

// Public macros used in this header and ruby.c
#define RUBY_INVOKE(RECV, NAME, ...) RUBY_INVOKE_IMPL(rb_tr_unwrap(RECV), NAME, ##__VA_ARGS__)
#define RUBY_INVOKE_NO_WRAP(RECV, NAME, ...) RUBY_INVOKE_IMPL_NO_WRAP(rb_tr_unwrap(RECV), NAME, ##__VA_ARGS__)

#define RUBY_CEXT_INVOKE(NAME, ...) RUBY_INVOKE_IMPL(RUBY_CEXT, NAME, ##__VA_ARGS__)
#define RUBY_CEXT_INVOKE_NO_WRAP(NAME, ...) RUBY_INVOKE_IMPL_NO_WRAP(RUBY_CEXT, NAME, ##__VA_ARGS__)
