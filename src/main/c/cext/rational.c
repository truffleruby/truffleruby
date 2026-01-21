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

// Rational, rb_rational_*

VALUE rb_Rational(VALUE num, VALUE den) {
  return RUBY_CEXT_INVOKE("rb_Rational", num, den);
}

VALUE rb_rational_raw(VALUE num, VALUE den) {
  return RUBY_CEXT_INVOKE("rb_rational_raw", num, den);
}

VALUE rb_rational_new(VALUE num, VALUE den) {
  return RUBY_CEXT_INVOKE("rb_rational_new", num, den);
}

VALUE rb_rational_num(VALUE rat) {
  return RUBY_INVOKE(rat, "numerator");
}

VALUE rb_rational_den(VALUE rat) {
  return RUBY_INVOKE(rat, "denominator");
}

VALUE rb_flt_rationalize_with_prec(VALUE value, VALUE precision) {
  return RUBY_CEXT_INVOKE("rb_flt_rationalize_with_prec", value, precision);
}

VALUE rb_flt_rationalize(VALUE value) {
    return RUBY_CEXT_INVOKE("rb_flt_rationalize", value);
}
