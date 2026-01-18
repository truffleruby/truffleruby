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

// Float, rb_float_*

bool rb_tr_float_type_p(VALUE obj) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("RB_FLOAT_TYPE_P", obj));
}

#undef rb_float_new
VALUE rb_float_new(double value) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_float_new", value));
}

VALUE rb_float_new_in_heap(double value) {
  return rb_float_new(value);
}

VALUE rb_Float(VALUE value) {
  return RUBY_CEXT_INVOKE("rb_Float", value);
}

#undef rb_float_value
double rb_float_value(VALUE value) {
  return polyglot_as_double(RUBY_CEXT_INVOKE_NO_WRAP("RFLOAT_VALUE", value));
}
