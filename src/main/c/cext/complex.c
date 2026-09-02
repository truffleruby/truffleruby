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

// Complex, rb_complex_*

VALUE rb_Complex(VALUE real, VALUE imag) {
  return rb_tr_up_rb_Complex(real, imag);
}

VALUE rb_complex_new(VALUE real, VALUE imag) {
  return rb_tr_up_rb_complex_new(real, imag);
}

VALUE rb_complex_raw(VALUE real, VALUE imag) {
  return rb_tr_up_rb_complex_raw(real, imag);
}

VALUE rb_complex_polar(VALUE r, VALUE theta) {
  return rb_tr_up_rb_complex_polar(r, theta);
}

VALUE rb_complex_real(VALUE complex) {
  return rb_tr_up_send0_real(complex);
}

VALUE rb_complex_imag(VALUE complex) {
  return rb_tr_up_send0_imag(complex);
}

VALUE rb_complex_set_real(VALUE complex, VALUE real) {
  return rb_tr_up_rb_complex_set_real(complex, real);
}

VALUE rb_complex_set_imag(VALUE complex, VALUE imag) {
  return rb_tr_up_rb_complex_set_imag(complex, imag);
}
