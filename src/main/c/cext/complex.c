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

// Complex, rb_complex_*

VALUE rb_Complex(VALUE real, VALUE imag) {
  return RUBY_CEXT_INVOKE("rb_Complex", real, imag);
}

VALUE rb_complex_new(VALUE real, VALUE imag) {
  return RUBY_CEXT_INVOKE("rb_complex_new", real, imag);
}

VALUE rb_complex_raw(VALUE real, VALUE imag) {
  return RUBY_CEXT_INVOKE("rb_complex_raw", real, imag);
}

VALUE rb_complex_polar(VALUE r, VALUE theta) {
  return RUBY_CEXT_INVOKE("rb_complex_polar", r, theta);
}

VALUE rb_complex_real(VALUE complex) {
  return RUBY_INVOKE(complex, "real");
}

VALUE rb_complex_imag(VALUE complex) {
  return RUBY_INVOKE(complex, "imag");
}

VALUE rb_complex_set_real(VALUE complex, VALUE real) {
  return RUBY_CEXT_INVOKE("rb_complex_set_real", complex, real);
}

VALUE rb_complex_set_imag(VALUE complex, VALUE imag) {
  return RUBY_CEXT_INVOKE("rb_complex_set_imag", complex, imag);
}
