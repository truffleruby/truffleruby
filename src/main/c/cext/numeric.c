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
#include <ruby/util.h>
#include <internal/numeric.h>

// Numeric conversions, rb_*num*, rb_fix2*

// coerce

VALUE rb_num_coerce_bin(VALUE x, VALUE y, ID func) {
  return RUBY_CEXT_INVOKE("rb_num_coerce_bin", x, y, ID2SYM(func));
}

VALUE rb_num_coerce_cmp(VALUE x, VALUE y, ID func) {
  return RUBY_CEXT_INVOKE("rb_num_coerce_cmp", x, y, ID2SYM(func));
}

VALUE rb_num_coerce_relop(VALUE x, VALUE y, ID func) {
  return RUBY_CEXT_INVOKE("rb_num_coerce_relop", x, y, ID2SYM(func));
}

VALUE rb_check_to_integer(VALUE object, const char *method) {
  return RUBY_CEXT_INVOKE("rb_check_to_integer", object, rb_str_new_cstr(method));
}

void rb_num_zerodiv(void) {
  rb_raise(rb_eZeroDivError, "divided by 0");
}

// Conversions between numeric types and from/to String

double ruby_strtod(const char *nptr, char **endptr) {
  // See ruby/util.h
  #undef strtod
  return strtod(nptr, endptr);
  #define strtod(s,e) ruby_strtod((s),(e))
}

unsigned long rb_num2ulong(VALUE val) {
  return (unsigned long)polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2ulong", val));
}

static char *out_of_range_float(char (*pbuf)[24], VALUE val) {
  char *const buf = *pbuf;
  char *s;

  snprintf(buf, sizeof(*pbuf), "%-.10g", RFLOAT_VALUE(val));
  if ((s = strchr(buf, ' ')) != 0) {
    *s = '\0';
  }
  return buf;
}

#define FLOAT_OUT_OF_RANGE(val, type) do { \
    char buf[24]; \
    rb_raise(rb_eRangeError, "float %s out of range of "type, \
       out_of_range_float(&buf, (val))); \
} while (0)

#define LLONG_MIN_MINUS_ONE ((double)LLONG_MIN-1)
#define LLONG_MAX_PLUS_ONE (2*(double)(LLONG_MAX/2+1))
#define ULLONG_MAX_PLUS_ONE (2*(double)(ULLONG_MAX/2+1))
#define LLONG_MIN_MINUS_ONE_IS_LESS_THAN(n) \
  (LLONG_MIN_MINUS_ONE == (double)LLONG_MIN ? \
   LLONG_MIN <= (n): \
   LLONG_MIN_MINUS_ONE < (n))

LONG_LONG rb_num2ll(VALUE val) {
  if (NIL_P(val)) {
    rb_raise(rb_eTypeError, "no implicit conversion from nil");
  }

  if (FIXNUM_P(val)) {
    return (LONG_LONG)FIX2LONG(val);
  } else if (RB_TYPE_P(val, T_FLOAT)) {
    double d = RFLOAT_VALUE(val);
    if (d < LLONG_MAX_PLUS_ONE && (LLONG_MIN_MINUS_ONE_IS_LESS_THAN(d))) {
      return (LONG_LONG)d;
    } else {
      FLOAT_OUT_OF_RANGE(val, "long long");
    }
  }
  else if (RB_TYPE_P(val, T_BIGNUM)) {
    return rb_big2ll(val);
  } else if (RB_TYPE_P(val, T_STRING)) {
    rb_raise(rb_eTypeError, "no implicit conversion from string");
  } else if (RB_TYPE_P(val, T_TRUE) || RB_TYPE_P(val, T_FALSE)) {
    rb_raise(rb_eTypeError, "no implicit conversion from boolean");
  }

  val = rb_to_int(val);
  return NUM2LL(val);
}

unsigned LONG_LONG rb_num2ull(VALUE val) {
  if (NIL_P(val)) {
    rb_raise(rb_eTypeError, "no implicit conversion from nil");
  }

  if (FIXNUM_P(val)) {
    return (LONG_LONG)FIX2LONG(val); /* this is FIX2LONG, intended */
  } else if (RB_TYPE_P(val, T_FLOAT)) {
    double d = RFLOAT_VALUE(val);
    if (d < ULLONG_MAX_PLUS_ONE && LLONG_MIN_MINUS_ONE_IS_LESS_THAN(d)) {
      if (0 <= d) {
        return (unsigned LONG_LONG)d;
      }
      return (unsigned LONG_LONG)(LONG_LONG)d;
    } else {
      FLOAT_OUT_OF_RANGE(val, "unsigned long long");
    }
  }
  else if (RB_TYPE_P(val, T_BIGNUM)) {
    return rb_big2ull(val);
  } else if (RB_TYPE_P(val, T_STRING)) {
    rb_raise(rb_eTypeError, "no implicit conversion from string");
  } else if (RB_TYPE_P(val, T_TRUE) || RB_TYPE_P(val, T_FALSE)) {
    rb_raise(rb_eTypeError, "no implicit conversion from boolean");
  }

  val = rb_to_int(val);
  return NUM2ULL(val);
}

short rb_num2short(VALUE value) {
  long long_val = rb_num2long(value);
  if ((long)(short)long_val != long_val) {
    rb_raise(rb_eRangeError, "integer %ld too %s to convert to 'short'",
       long_val, long_val < 0 ? "small" : "big");
  }
  return long_val;
}

unsigned short rb_num2ushort(VALUE value) {
  unsigned long long_val = rb_num2ulong(value);
  if ((unsigned long)(unsigned short)long_val != long_val) {
    rb_raise(rb_eRangeError, "integer %ld too %s to convert to 'unsigned short'",
       long_val, long_val < 0 ? "small" : "big");
  }
  return long_val;
}

short rb_fix2short(VALUE value) {
  return rb_num2short(value);
}

long rb_fix2int(VALUE value) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_fix2int", value));
}

unsigned long rb_fix2uint(VALUE value) {
  return polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_fix2uint", value));
}

int rb_long2int(long value) {
  return polyglot_as_i64(polyglot_invoke(RUBY_CEXT, "rb_long2int", value));
}

VALUE rb_int2inum(intptr_t n) {
  return LONG2NUM(n);
}

VALUE rb_uint2inum(uintptr_t n) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_ulong2num", n));
}

VALUE rb_uint2big(uintptr_t i) {
  return rb_uint2inum(i);
}

VALUE rb_ll2inum(LONG_LONG n) {
  /* Long and long long are both 64-bits with clang x86-64. */
  return LONG2NUM(n);
}

VALUE rb_ull2inum(unsigned LONG_LONG val) {
  /* Long and long long are both 64-bits with clang x86-64. */
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_ulong2num", val));
}

double rb_num2dbl(VALUE val) {
  return polyglot_as_double(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2dbl", val));
}

long rb_num2int(VALUE val) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2int", val));
}

unsigned long rb_num2uint(VALUE val) {
  return (unsigned long)polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2uint", val));
}

long rb_num2long(VALUE val) {
  return polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2long", val));
}

VALUE rb_cstr_to_inum(const char* string, int base, int raise) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_cstr_to_inum", rb_tr_unwrap(rb_str_new_cstr(string)), base, raise));
}

double rb_cstr_to_dbl(const char* string, int badcheck) {
  return polyglot_as_double(RUBY_CEXT_INVOKE_NO_WRAP("rb_cstr_to_dbl", rb_str_new_cstr(string), rb_boolean(badcheck)));
}

double rb_big2dbl(VALUE x) {
  return polyglot_as_double(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2dbl", x));
}

VALUE rb_dbl2big(double d) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "DBL2BIG", d));
}

LONG_LONG rb_big2ll(VALUE x) {
  return polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2long", x));
}

unsigned LONG_LONG rb_big2ull(VALUE x) {
  return polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2ulong", x));
}

long rb_big2long(VALUE x) {
  return polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2long", x));
}

unsigned long rb_big2ulong(VALUE x) {
  return polyglot_as_i64(RUBY_CEXT_INVOKE_NO_WRAP("rb_num2ulong", x));
}

VALUE rb_cstr2inum(const char *string, int base) {
  return rb_cstr_to_inum(string, base, base==0);
}

VALUE rb_str_to_inum(VALUE str, int base, int badcheck) {
  char *s;
  StringValue(str);
  rb_must_asciicompat(str);
  if (badcheck) {
    s = StringValueCStr(str);
  } else {
    s = RSTRING_PTR(str);
  }
  return rb_cstr_to_inum(s, base, badcheck);
}

VALUE rb_str2inum(VALUE string, int base) {
  return rb_str_to_inum(string, base, base==0);
}

VALUE rb_fix2str(VALUE x, int base) {
  return RUBY_CEXT_INVOKE("rb_fix2str", x, INT2FIX(base));
}

VALUE rb_big2str(VALUE x, int base) {
  return rb_fix2str(x, base);
}

VALUE rb_to_int(VALUE object) {
  return RUBY_CEXT_INVOKE("rb_to_int", object);
}
