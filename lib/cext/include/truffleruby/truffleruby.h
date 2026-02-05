/*
* Copyright (c) 2016, 2025 Oracle and/or its affiliates. All rights reserved. This
* code is released under a tri EPL/GPL/LGPL license. You can use it,
* redistribute it and/or modify it under the terms of the:
*
* Eclipse Public License version 2.0
* GNU General Public License version 2
* GNU Lesser General Public License version 2.1
*
* This file contains code that is based on the Ruby API headers,
* copyright (C) Yukihiro Matsumoto, licensed under the 2-clause BSD licence.
*/

#ifndef TRUFFLERUBY_H
#define TRUFFLERUBY_H

#if defined(__cplusplus)
extern "C" {
#endif

// These refer Ruby global variables and their value can change,
// so we use macros instead of C global variables like MRI, which would be complicated to update.
VALUE rb_tr_stdin(void);
VALUE rb_tr_stdout(void);
VALUE rb_tr_stderr(void);
VALUE rb_tr_fs(void);
VALUE rb_tr_output_fs(void);
VALUE rb_tr_rs(void);
VALUE rb_tr_output_rs(void);
VALUE rb_tr_default_rs(void);

#define rb_stdin rb_tr_stdin()
#define rb_stdout rb_tr_stdout()
#define rb_stderr rb_tr_stderr()
#define rb_fs rb_tr_fs()
#define rb_output_fs rb_tr_output_fs()
#define rb_rs rb_tr_rs()
#define rb_output_rs rb_tr_output_rs()
#define rb_default_rs rb_tr_default_rs()

// A typedef for the callback argument of rb_thread_call_with_gvl()

typedef void* (gvl_call)(void *);

// Exceptions

#define rb_raise(EXCEPTION, ...) \
  rb_exc_raise(rb_exc_new_str(EXCEPTION, rb_sprintf(__VA_ARGS__)))

// Macros for rb_funcall(). As written they currently only work on Sulong.
//
// We use this pair of macros because ##__VA_ARGS__ args will not
// have macro substitution done on them at the right point in
// preprocessing and will prevent rb_funcall(..., rb_funcall(...))
// from being expanded correctly.
/*
#define RUBY_FUNCALL_IMPL_0(recv, name) polyglot_invoke(recv, name)
#define RUBY_FUNCALL_IMPL_1(recv, name, V1) polyglot_invoke(recv, name, rb_tr_unwrap(V1))
#define RUBY_FUNCALL_IMPL_2(recv, name, V1, V2) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2))
#define RUBY_FUNCALL_IMPL_3(recv, name, V1, V2, V3) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3))
#define RUBY_FUNCALL_IMPL_4(recv, name, V1, V2, V3, V4) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4))
#define RUBY_FUNCALL_IMPL_5(recv, name, V1, V2, V3, V4, V5) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5))
#define RUBY_FUNCALL_IMPL_6(recv, name, V1, V2, V3, V4, V5, V6) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6))
#define RUBY_FUNCALL_IMPL_7(recv, name, V1, V2, V3, V4, V5, V6, V7) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7))
#define RUBY_FUNCALL_IMPL_8(recv, name, V1, V2, V3, V4, V5, V6, V7, V8) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8))
#define RUBY_FUNCALL_IMPL_9(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9))
#define RUBY_FUNCALL_IMPL_10(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10))
#define RUBY_FUNCALL_IMPL_11(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11))
#define RUBY_FUNCALL_IMPL_12(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12))
#define RUBY_FUNCALL_IMPL_13(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13))
#define RUBY_FUNCALL_IMPL_14(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14))
#define RUBY_FUNCALL_IMPL_15(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15))
#define RUBY_FUNCALL_IMPL_16(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15), rb_tr_unwrap(V16))
#define RUBY_FUNCALL_IMPL_17(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15), rb_tr_unwrap(V16), rb_tr_unwrap(V17))
#define RUBY_FUNCALL_IMPL_18(recv, name, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10, V11, V12, V13, V14, V15, V16, V17, V18) polyglot_invoke(recv, name, rb_tr_unwrap(V1), rb_tr_id2sym(V2), rb_tr_unwrap(V3), rb_tr_unwrap(V4), rb_tr_unwrap(V5), rb_tr_unwrap(V6), rb_tr_unwrap(V7), rb_tr_unwrap(V8), rb_tr_unwrap(V9), rb_tr_unwrap(V10), rb_tr_unwrap(V11), rb_tr_unwrap(V12), rb_tr_unwrap(V13), rb_tr_unwrap(V14), rb_tr_unwrap(V15), rb_tr_unwrap(V16), rb_tr_unwrap(V17), rb_tr_unwrap(V18))
#define FUNCALL_IMPL(RECV, MESG, _1, _2, _3, _4, _5, _6, _7, _8, _9, _10, _11, _12, _13, _14, _15, _16, _17, _18, NAME, ...) NAME
#define RUBY_FUNCALL_IMPL_NO_WRAP(RECV, NAME, ...) FUNCALL_IMPL(RECV, NAME, ##__VA_ARGS__, RUBY_FUNCALL_IMPL_18, RUBY_FUNCALL_IMPL_17, RUBY_FUNCALL_IMPL_16, RUBY_FUNCALL_IMPL_15, RUBY_FUNCALL_IMPL_14, RUBY_FUNCALL_IMPL_13, RUBY_FUNCALL_IMPL_12, RUBY_FUNCALL_IMPL_11, RUBY_FUNCALL_IMPL_10, RUBY_FUNCALL_IMPL_9, RUBY_FUNCALL_IMPL_8, RUBY_FUNCALL_IMPL_7, RUBY_FUNCALL_IMPL_6, RUBY_FUNCALL_IMPL_5, RUBY_FUNCALL_IMPL_4, RUBY_FUNCALL_IMPL_3, RUBY_FUNCALL_IMPL_2, RUBY_FUNCALL_IMPL_1, RUBY_FUNCALL_IMPL_0)(RECV, NAME, ##__VA_ARGS__)
#define RUBY_FUNCALL_IMPL(RECV, NAME, ...) rb_tr_wrap(RUBY_FUNCALL_IMPL_NO_WRAP(RECV, NAME, ##__VA_ARGS__))
#define RUBY_CEXT_FUNCALL(NAME, ...) RUBY_FUNCALL_IMPL(RUBY_CEXT, NAME, ##__VA_ARGS__)

#define rb_tr_funcall(object, method, n,...) RUBY_CEXT_FUNCALL("rb_funcall", object, ID2SYM(method), INT2FIX(n), ##__VA_ARGS__)
#define rb_funcall(object, method, ...) rb_tr_funcall(object, method, __VA_ARGS__)
*/

// Optimizations for rb_iv_get() and rb_iv_set()

#define rb_iv_get(obj, name) \
  (__builtin_constant_p(name) ? \
   rb_ivar_get(obj, rb_intern(name)) : \
   rb_iv_get(obj, name))

#define rb_iv_set(obj, name, val) \
  (__builtin_constant_p(name) ? \
   rb_ivar_set(obj, rb_intern(name), val) : \
   rb_iv_set(obj, name, val))

// rb_scan_args() and rb_scan_args_kw() implementation

struct rb_tr_scan_args_parse_data {
  int pre;
  int optional;
  bool rest;
  int post;
  bool kwargs;
  bool block;
};

#define rb_tr_scan_args_kw(kw_flag, argc, argv, format, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10) \
  (RBIMPL_CONSTANT_P(format) ?                                             \
     __extension__ ({ \
         static bool rb_tr_scan_args_format_str_evaled; \
         static struct rb_tr_scan_args_parse_data rb_tr_scan_data; \
         if (!rb_tr_scan_args_format_str_evaled) { \
             rb_tr_scan_args_kw_parse(format, &rb_tr_scan_data); \
             rb_tr_scan_args_format_str_evaled = true; \
         } \
         rb_tr_scan_args_kw_int(kw_flag, argc, argv, rb_tr_scan_data, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10); \
     }) : \
  rb_tr_scan_args_kw_non_const(kw_flag, argc, argv, format, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10))

#define rb_tr_scan_args_kw_1(KW_FLAG, ARGC, ARGV, FORMAT, V1) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_2(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_3(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, NULL, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_4(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, NULL, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_5(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, NULL, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_6(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, NULL, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_7(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, NULL, NULL, NULL)
#define rb_tr_scan_args_kw_8(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, NULL, NULL)
#define rb_tr_scan_args_kw_9(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, NULL)
#define rb_tr_scan_args_kw_10(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10) rb_tr_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, V1, V2, V3, V4, V5, V6, V7, V8, V9, V10)

#define SCAN_ARGS_KW_IMPL(_1, _2, _3, _4, _5, _6, _7, _8, _9, _10, NAME, ...) NAME
#define rb_scan_args_kw(KW_FLAG, ARGC, ARGV, FORMAT, ...) SCAN_ARGS_KW_IMPL(__VA_ARGS__, rb_tr_scan_args_kw_10, rb_tr_scan_args_kw_9, rb_tr_scan_args_kw_8, rb_tr_scan_args_kw_7, rb_tr_scan_args_kw_6, rb_tr_scan_args_kw_5, rb_tr_scan_args_kw_4, rb_tr_scan_args_kw_3, rb_tr_scan_args_kw_2, rb_tr_scan_args_kw_1)(KW_FLAG, ARGC, ARGV, FORMAT, __VA_ARGS__)

#define rb_scan_args(ARGC, ARGV, FORMAT, ...) SCAN_ARGS_KW_IMPL(__VA_ARGS__, rb_tr_scan_args_kw_10, rb_tr_scan_args_kw_9, rb_tr_scan_args_kw_8, rb_tr_scan_args_kw_7, rb_tr_scan_args_kw_6, rb_tr_scan_args_kw_5, rb_tr_scan_args_kw_4, rb_tr_scan_args_kw_3, rb_tr_scan_args_kw_2, rb_tr_scan_args_kw_1)(RB_SCAN_ARGS_PASS_CALLED_KEYWORDS, ARGC, ARGV, FORMAT, __VA_ARGS__)

void rb_tr_scan_args_kw_parse(const char *format, struct rb_tr_scan_args_parse_data *parse_data);

ALWAYS_INLINE(static int rb_tr_scan_args_kw_int(int kw_flag, int argc, VALUE *argv, struct rb_tr_scan_args_parse_data parse_data, VALUE *v1, VALUE *v2, VALUE *v3, VALUE *v4, VALUE *v5, VALUE *v6, VALUE *v7, VALUE *v8, VALUE *v9, VALUE *v10));
static inline int rb_tr_scan_args_kw_int(int kw_flag, int argc, VALUE *argv, struct rb_tr_scan_args_parse_data parse_data, VALUE *v1, VALUE *v2, VALUE *v3, VALUE *v4, VALUE *v5, VALUE *v6, VALUE *v7, VALUE *v8, VALUE *v9, VALUE *v10) {

  int keyword_given = 0;
  // int last_hash_keyword = 0;

  switch (kw_flag) {
    case RB_SCAN_ARGS_PASS_CALLED_KEYWORDS: break;
    case RB_SCAN_ARGS_KEYWORDS: keyword_given = 1; break;
    case RB_SCAN_ARGS_LAST_HASH_KEYWORDS: /* last_hash_keyword = 1; not used currently */ break;
  }

  // Check we have enough arguments

  if (parse_data.pre + parse_data.post > argc) {
    rb_raise(rb_eArgError, "not enough arguments for required");
  }

  const int n_mand = parse_data.pre + parse_data.post;
  const int n_opt = parse_data.optional;

  // Read arguments

  int argn = 0;
  int valuen = 1; // We've numbered the v parameters from 1
  bool taken_rest = false;
  bool taken_block = false;
  bool taken_kwargs = false;
  // Indicates that although the function can take kwargs they aren't
  // actually being consumed from the provided arguments. The variable
  // accepting them will still need to be set to Qnil in such cases.
  bool erased_kwargs = false;
  bool found_kwargs = false;
  VALUE hash = Qnil;

  /* capture an option hash - phase 1: pop */
  /* Ignore final positional hash if empty keywords given */
  if (argc > 0) {
    VALUE last = argv[argc - 1];

    if (parse_data.kwargs && n_mand < argc) {
      if (keyword_given) {
        if (!RB_TYPE_P(last, T_HASH)) {
          rb_warn("Keyword flag set when calling rb_scan_args, but last entry is not a Hash");
        }
        else {
          hash = last;
        }
      }

      else {
        hash = rb_check_hash_type(last);
        if (NIL_P(hash)) {
          parse_data.kwargs = false;
          erased_kwargs = true;
        }
      }
    }
    else if (parse_data.kwargs && keyword_given && n_mand == argc) {
      /* Warn if treating keywords as positional, as in Ruby 3, this will be an error */
      rb_warn("Passing the keyword argument as the last hash parameter is deprecated");
    }
  }

  int trailing = parse_data.post;

  if (parse_data.kwargs) {
    trailing++;
  }

  while (true) {
    // Get the next argument

    VALUE arg;

    if (parse_data.pre > 0 || parse_data.optional > 0) {
      if (argn - parse_data.pre < argc - trailing) {
        arg = argv[argn];
        argn++;
      } else {
        arg = Qnil;
      }

      if (parse_data.pre > 0) {
        parse_data.pre--;
      } else {
        parse_data.optional--;
      }
    } else if (parse_data.rest && !taken_rest) {
      arg = rb_ary_new();
      while (argn < argc - trailing) {
        rb_ary_push(arg, argv[argn]);
        argn++;
      }
      taken_rest = true;
    } else if (parse_data.post > 0) {
      arg = argv[argn];
      argn++;
      parse_data.post--;
    } else if (parse_data.kwargs && !taken_kwargs) {
       if (argn < argc) {
        arg = argv[argn];
        if (!RB_TYPE_P(arg, T_HASH)) {
          rb_warn("Keyword flag set when calling rb_scan_args, but last entry is not a Hash");
        }
        argn++;
        found_kwargs = true;
      } else {
        arg = Qnil;
      }
      taken_kwargs = true;
    } else if (erased_kwargs && !taken_kwargs) {
      arg = Qnil;
      taken_kwargs = true;
    } else if (parse_data.block && !taken_block) {
      if (rb_block_given_p()) {
        arg = rb_block_proc();
      } else {
        arg = Qnil;
      }
      taken_block = true;
    } else {
      break;
    }

    // Put the argument into the current value pointer

    // Don't assign the correct v to a temporary VALUE* and then assign arg to it - this doesn't optimise well

    switch (valuen) {
    case 1: if (v1 != NULL) { *v1 = arg; } break;
    case 2: if (v2 != NULL) { *v2 = arg; } break;
    case 3: if (v3 != NULL) { *v3 = arg; } break;
    case 4: if (v4 != NULL) { *v4 = arg; } break;
    case 5: if (v5 != NULL) { *v5 = arg; } break;
    case 6: if (v6 != NULL) { *v6 = arg; } break;
    case 7: if (v7 != NULL) { *v7 = arg; } break;
    case 8: if (v8 != NULL) { *v8 = arg; } break;
    case 9: if (v9 != NULL) { *v9 = arg; } break;
    case 10: if (v10 != NULL) { *v10 = arg; } break;
    }

    valuen++;
  }

  if (found_kwargs) {
    argc = argc - 1;
  }

  if (argn < argc) {
    rb_error_arity(argc, n_mand, parse_data.rest ? UNLIMITED_ARGUMENTS : n_mand + n_opt);
  }

  return argc;
}

ALWAYS_INLINE(static int rb_tr_scan_args_kw_non_const(int kw_flag, int argc, VALUE *argv, const char *format, VALUE *v1, VALUE *v2, VALUE *v3, VALUE *v4, VALUE *v5, VALUE *v6, VALUE *v7, VALUE *v8, VALUE *v9, VALUE *v10));
static inline int rb_tr_scan_args_kw_non_const(int kw_flag, int argc, VALUE *argv, const char *format, VALUE *v1, VALUE *v2, VALUE *v3, VALUE *v4, VALUE *v5, VALUE *v6, VALUE *v7, VALUE *v8, VALUE *v9, VALUE *v10) {

  struct rb_tr_scan_args_parse_data parse_data = {0, 0, false, 0, false, false};

  rb_tr_scan_args_kw_parse(format, &parse_data);

  return rb_tr_scan_args_kw_int(kw_flag, argc, argv, parse_data, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10);
}

#if defined(__cplusplus)
}
#endif

#endif
