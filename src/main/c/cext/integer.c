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

// Integer, rb_integer_*, rb_*int*, rb_big_*

bool rb_tr_integer_type_p(VALUE obj) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("rb_integer_type_p", obj));
}

VALUE rb_Integer(VALUE value) {
  return RUBY_CEXT_INVOKE("rb_Integer", value);
}

#define INTEGER_PACK_WORDORDER_MASK \
  (INTEGER_PACK_MSWORD_FIRST | \
   INTEGER_PACK_LSWORD_FIRST)
#define INTEGER_PACK_BYTEORDER_MASK \
  (INTEGER_PACK_MSBYTE_FIRST | \
   INTEGER_PACK_LSBYTE_FIRST | \
   INTEGER_PACK_NATIVE_BYTE_ORDER)
#define INTEGER_PACK_SUPPORTED_FLAG \
  (INTEGER_PACK_MSWORD_FIRST | \
   INTEGER_PACK_LSWORD_FIRST | \
   INTEGER_PACK_MSBYTE_FIRST | \
   INTEGER_PACK_LSBYTE_FIRST | \
   INTEGER_PACK_NATIVE_BYTE_ORDER | \
   INTEGER_PACK_2COMP | \
   INTEGER_PACK_FORCE_GENERIC_IMPLEMENTATION)

static void validate_integer_pack_format(size_t numwords, size_t wordsize, size_t nails, int flags, int supported_flags) {
  int wordorder_bits = flags & INTEGER_PACK_WORDORDER_MASK;
  int byteorder_bits = flags & INTEGER_PACK_BYTEORDER_MASK;

  if (flags & ~supported_flags) {
    rb_raise(rb_eArgError, "unsupported flags specified");
  }

  if (wordorder_bits == 0) {
    if (1 < numwords) {
      rb_raise(rb_eArgError, "word order not specified");
    }
  } else if (wordorder_bits != INTEGER_PACK_MSWORD_FIRST &&
      wordorder_bits != INTEGER_PACK_LSWORD_FIRST) {
    rb_raise(rb_eArgError, "unexpected word order");
  }

  if (byteorder_bits == 0) {
    rb_raise(rb_eArgError, "byte order not specified");
  } else if (byteorder_bits != INTEGER_PACK_MSBYTE_FIRST &&
    byteorder_bits != INTEGER_PACK_LSBYTE_FIRST &&
    byteorder_bits != INTEGER_PACK_NATIVE_BYTE_ORDER) {
      rb_raise(rb_eArgError, "unexpected byte order");
  }

  if (wordsize == 0) {
    rb_raise(rb_eArgError, "invalid wordsize: %lu", wordsize);
  }

  if (8 < wordsize) {
    rb_raise(rb_eArgError, "too big wordsize: %lu", wordsize);
  }

  if (wordsize <= nails / CHAR_BIT) {
    rb_raise(rb_eArgError, "too big nails: %lu", nails);
  }

  if (INT_MAX / wordsize < numwords) {
    rb_raise(rb_eArgError, "too big numwords * wordsize: %lu * %lu", numwords, wordsize);
  }
}

static int check_msw_first(int flags) {
  return flags & INTEGER_PACK_MSWORD_FIRST;
}

static int endian_swap(int flags) {
  return flags & INTEGER_PACK_MSBYTE_FIRST;
}

int rb_integer_pack(VALUE value, void *words, size_t numwords, size_t wordsize, size_t nails, int flags) {
  VALUE msw_first = rb_boolean(check_msw_first(flags));
  VALUE twosComp = rb_boolean(((flags & INTEGER_PACK_2COMP) != 0));
  VALUE swap = rb_boolean(endian_swap(flags));
  // Test for fixnum and do the right things here.
  void* bytes = polyglot_invoke(RUBY_CEXT, "rb_integer_bytes", rb_tr_unwrap(value),
                          (int)numwords, (int)wordsize, rb_tr_unwrap(msw_first), rb_tr_unwrap(twosComp), rb_tr_unwrap(swap));
  int size = (twosComp == Qtrue) ? polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_2scomp_bit_length", value))
    : polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_absint_bit_length", value));

  int sign;
  if (RB_FIXNUM_P(value)) {
    long l = NUM2LONG(value);
    sign = (l > 0) - (l < 0);
  } else {
    sign = polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_int_cmp", value, INT2FIX(0)));
  }
  int bytes_needed = size / 8 + (size % 8 == 0 ? 0 : 1);
  int words_needed = bytes_needed / wordsize + (bytes_needed % wordsize == 0 ? 0 : 1);
  int result = (words_needed <= numwords ? 1 : 2) * sign;

  uint8_t *buf = (uint8_t *)words;
  for (long i = 0; i < numwords * wordsize; i++) {
    buf[i] = (uint8_t) polyglot_as_i32(polyglot_get_array_element(bytes, i));
  }
  return result;
}

VALUE rb_int_positive_pow(long x, unsigned long y) {
  return RUBY_CEXT_INVOKE("rb_int_positive_pow", INT2FIX(x), INT2FIX(y));
}

// Needed to gem install cbor
VALUE rb_integer_unpack(const void *words, size_t numwords, size_t wordsize, size_t nails, int flags) {
  rb_tr_not_implemented("rb_integer_unpack");
}

size_t rb_absint_size(VALUE value, int *nlz_bits_ret) {
  int size = polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_absint_bit_length", value));
  if (nlz_bits_ret != NULL) {
    *nlz_bits_ret = size % 8;
  }
  int bytes = size / 8;
  if (size % 8 > 0) {
    bytes++;
  }
  return bytes;
}

int rb_big_sign(VALUE x) {
  return RTEST(RUBY_CEXT_INVOKE("rb_big_sign", x)) ? 1 : 0;
}

int rb_cmpint(VALUE val, VALUE a, VALUE b) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_cmpint", val, a, b));
}

VALUE rb_big_cmp(VALUE x, VALUE y) {
  return RUBY_CEXT_INVOKE("rb_big_cmp", x, y);
}

void rb_big_pack(VALUE val, unsigned long *buf, long num_longs) {
  rb_integer_pack(val, buf, num_longs, 8, 0,
                  INTEGER_PACK_2COMP | INTEGER_PACK_NATIVE_BYTE_ORDER | INTEGER_PACK_LSWORD_FIRST);
}

int rb_absint_singlebit_p(VALUE val) {
  return polyglot_as_i32(RUBY_CEXT_INVOKE_NO_WRAP("rb_absint_singlebit_p", val));
}

VALUE rb_int2big(intptr_t n) {
  // intptr_t is the same as long
  return rb_tr_longwrap(n);
}
