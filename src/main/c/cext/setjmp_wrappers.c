/*
 * Copyright (c) 2026 TruffleRuby contributors
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

// The rb_tr_setjmp_wrapper_* functions wrap every Ruby -> native call
// (Init_ functions, method implementations, callbacks like rb_define_method
// functions and mark/free/foreach functions). They RUBY_SETJMP() before the
// call so that a Ruby exception raised inside a native -> Java upcall can
// unwind the native frames with RUBY_LONGJMP() (see upcalls.h), like
// rb_raise() does in MRI. The Java side then rethrows the stored exception
// when the downcall returns.

#include <truffleruby-impl.h>
#include <setjmp.h>
#include <stdio.h>
#include <stdlib.h>

__thread jmp_buf *rb_tr_jmp_buf = NULL;

void rb_tr_longjmp_from_java_exception(void) {
  if (LIKELY(rb_tr_jmp_buf != NULL)) {
    RUBY_LONGJMP(*rb_tr_jmp_buf, 1);
  } else {
    fprintf(stderr, "ERROR: a Ruby exception was raised in a C extension upcall but rb_tr_jmp_buf is NULL.\n");
    abort();
  }
}

void rb_tr_setjmp_wrapper_void_to_void(void (*func)(void)) {
  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    func();
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
  }

  rb_tr_jmp_buf = prev_jmp_buf;
}

void rb_tr_setjmp_wrapper_pointer1_to_void(void (*func)(VALUE arg), VALUE arg) {
  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    func(arg);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
  }

  rb_tr_jmp_buf = prev_jmp_buf;
}

void rb_tr_setjmp_wrapper_pointer2_to_void(void (*func)(VALUE tracepoint, void *data), VALUE tracepoint, void *data) {
  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    func(tracepoint, data);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
  }

  rb_tr_jmp_buf = prev_jmp_buf;
}

int rb_tr_setjmp_wrapper_pointer2_to_int(int (*func)(VALUE element, VALUE arg), VALUE element, VALUE arg) {
  int result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(element, arg);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

void rb_tr_setjmp_wrapper_pointer3_to_void(void (*func)(VALUE val, ID id, VALUE *data), VALUE val, ID id, VALUE *data) {
  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    func(val, id, data);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
  }

  rb_tr_jmp_buf = prev_jmp_buf;
}

int rb_tr_setjmp_wrapper_pointer3_to_int(int (*func)(VALUE key, VALUE val, VALUE arg), VALUE key, VALUE val, VALUE arg) {
  int result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(key, val, arg);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

size_t rb_tr_setjmp_wrapper_pointer1_to_size_t(size_t (*func)(const void *arg), const void *arg) {
  size_t result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_int_pointer2_to_pointer(VALUE (*func)(int argc, VALUE *argv, VALUE obj), int argc, VALUE *argv, VALUE obj) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(argc, argv, obj);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer2_int_to_pointer(VALUE (*func)(VALUE g, VALUE h, int r), VALUE g, VALUE h, int r) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(g, h, r);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer2_int_pointer2_to_pointer(VALUE (*func)(VALUE yielded_arg, VALUE callback_arg, int argc, const VALUE *argv, VALUE blockarg), VALUE yielded_arg, VALUE callback_arg, int argc, const VALUE *argv, VALUE blockarg) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(yielded_arg, callback_arg, argc, argv, blockarg);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer1_to_pointer(VALUE (*func)(VALUE arg1), VALUE arg1) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer2_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2), VALUE arg1, VALUE arg2) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer3_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3), VALUE arg1, VALUE arg2, VALUE arg3) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer4_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer5_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer6_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer7_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer8_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer9_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer10_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer11_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer12_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer13_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer14_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13, VALUE arg14), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13, VALUE arg14) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer15_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13, VALUE arg14, VALUE arg15), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13, VALUE arg14, VALUE arg15) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}

VALUE rb_tr_setjmp_wrapper_pointer16_to_pointer(VALUE (*func)(VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13, VALUE arg14, VALUE arg15, VALUE arg16), VALUE arg1, VALUE arg2, VALUE arg3, VALUE arg4, VALUE arg5, VALUE arg6, VALUE arg7, VALUE arg8, VALUE arg9, VALUE arg10, VALUE arg11, VALUE arg12, VALUE arg13, VALUE arg14, VALUE arg15, VALUE arg16) {
  VALUE result;

  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf;
  jmp_buf here;
  rb_tr_jmp_buf = &here;

  if (RUBY_SETJMP(here) == 0) {
    result = func(arg1, arg2, arg3, arg4, arg5, arg6, arg7, arg8, arg9, arg10, arg11, arg12, arg13, arg14, arg15, arg16);
  } else {
    // The exception is rethrown by Java when this downcall returns, so the return value does not matter
    result = Qundef;
  }

  rb_tr_jmp_buf = prev_jmp_buf;

  return result;
}


// Wrappers for (int argc, VALUE *argv, VALUE obj) functions (rb_define_method with argc=-1),
// receiving the arguments individually like the fixed-arity wrappers above and building argv on
// the native stack, so no native allocation is needed to pass the arguments (see
// Primitive.cext_invoke_argv). More than 15 arguments are passed by
// rb_tr_setjmp_wrapper_int_pointer2_to_pointer with an argv in the thread's native buffer.
#define RB_TR_ARGV_WRAPPER(N, PARAMS, ...) \
VALUE rb_tr_setjmp_wrapper_argv##N##_to_pointer(VALUE (*func)(int argc, VALUE *argv, VALUE obj), VALUE obj PARAMS) { \
  VALUE argv[N > 0 ? N : 1] = { __VA_ARGS__ }; \
  VALUE result; \
  jmp_buf *prev_jmp_buf = rb_tr_jmp_buf; \
  jmp_buf here; \
  rb_tr_jmp_buf = &here; \
  if (RUBY_SETJMP(here) == 0) { \
    result = func(N, argv, obj); \
  } else { \
    /* The exception is rethrown by Java when this downcall returns, so the return value does not matter */ \
    result = Qundef; \
  } \
  rb_tr_jmp_buf = prev_jmp_buf; \
  return result; \
}

#define A(i) , VALUE a##i
RB_TR_ARGV_WRAPPER(0, , Qnil)
RB_TR_ARGV_WRAPPER(1, A(0), a0)
RB_TR_ARGV_WRAPPER(2, A(0) A(1), a0, a1)
RB_TR_ARGV_WRAPPER(3, A(0) A(1) A(2), a0, a1, a2)
RB_TR_ARGV_WRAPPER(4, A(0) A(1) A(2) A(3), a0, a1, a2, a3)
RB_TR_ARGV_WRAPPER(5, A(0) A(1) A(2) A(3) A(4), a0, a1, a2, a3, a4)
RB_TR_ARGV_WRAPPER(6, A(0) A(1) A(2) A(3) A(4) A(5), a0, a1, a2, a3, a4, a5)
RB_TR_ARGV_WRAPPER(7, A(0) A(1) A(2) A(3) A(4) A(5) A(6), a0, a1, a2, a3, a4, a5, a6)
RB_TR_ARGV_WRAPPER(8, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7), a0, a1, a2, a3, a4, a5, a6, a7)
RB_TR_ARGV_WRAPPER(9, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8), a0, a1, a2, a3, a4, a5, a6, a7, a8)
RB_TR_ARGV_WRAPPER(10, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8) A(9), a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)
RB_TR_ARGV_WRAPPER(11, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8) A(9) A(10), a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)
RB_TR_ARGV_WRAPPER(12, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8) A(9) A(10) A(11), a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11)
RB_TR_ARGV_WRAPPER(13, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8) A(9) A(10) A(11) A(12), a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12)
RB_TR_ARGV_WRAPPER(14, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8) A(9) A(10) A(11) A(12) A(13), a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13)
RB_TR_ARGV_WRAPPER(15, A(0) A(1) A(2) A(3) A(4) A(5) A(6) A(7) A(8) A(9) A(10) A(11) A(12) A(13) A(14), a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14)
#undef A
#undef RB_TR_ARGV_WRAPPER
