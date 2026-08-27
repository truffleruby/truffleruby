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

// Array, rb_ary_*

long rb_array_len(VALUE array) {
  return rb_tr_up_rb_array_len(array);
}

int RARRAY_LENINT(VALUE array) {
  return (int) rb_tr_up_rb_array_len(array);
}

VALUE rb_tr_rarray_aref(VALUE array, long index) {
  return rb_tr_up_send1_aref(array, LONG2NUM(index));
}

VALUE rb_Array(VALUE array) {
  return rb_tr_up_rb_Array(array);
}

VALUE *RARRAY_PTR_IMPL(VALUE array) {
  return (VALUE *) rb_tr_up_RARRAY_PTR(array);
}

VALUE rb_ary_new(void) {
  return rb_tr_up_rb_ary_new();
}

VALUE rb_ary_new_capa(long capacity) {
  return rb_tr_up_rb_ary_new_capa(capacity);
}

VALUE rb_tr_ary_new_from_args_va_list(long n, va_list args) {
  VALUE values[n];
  for (int i = 0; i < n; i++) {
    values[i] = va_arg(args, VALUE);
  }
  return rb_ary_new_from_values(n, values);
}

VALUE rb_ary_new_from_values(long n, const VALUE *values) {
  return rb_tr_up_rb_ary_new_from_values(values, n);
}

VALUE rb_assoc_new(VALUE a, VALUE b) {
  return rb_ary_new3(2, a, b);
}

VALUE rb_ary_push(VALUE array, VALUE value) {
  rb_tr_up_send1_o_push(array, value);
  return array;
}

VALUE rb_ary_pop(VALUE array) {
  return rb_tr_up_send0_pop(array);
}

VALUE rb_ary_sort(VALUE array) {
  return rb_tr_up_send0_sort(array);
}

VALUE rb_ary_sort_bang(VALUE array) {
  return rb_tr_up_send0_sort_bang(array);
}

void rb_ary_store(VALUE array, long index, VALUE value) {
  rb_tr_up_send2_o_aset(array, LONG2FIX(index), value);
}

VALUE rb_ary_entry(VALUE array, long index) {
  return rb_tr_up_send1_aref(array, LONG2NUM(index));
}

VALUE rb_ary_unshift(VALUE array, VALUE value) {
  return rb_tr_up_send1_unshift(array, value);
}

VALUE rb_ary_aref(int n, const VALUE* values, VALUE array) {
  return rb_tr_up_send_splatted(array, rb_str_new_cstr("[]"), rb_ary_new4(n, values));
}

VALUE rb_ary_clear(VALUE array) {
  return rb_tr_up_send0_clear(array);
}

VALUE rb_ary_delete(VALUE array, VALUE value) {
  return rb_tr_up_send1_delete(array, value);
}

VALUE rb_ary_delete_at(VALUE array, long n) {
  return rb_tr_up_send1_delete_at(array, LONG2NUM(n));
}

VALUE rb_ary_includes(VALUE array, VALUE value) {
  return rb_tr_up_send1_include_p(array, value);
}

VALUE rb_ary_join(VALUE array, VALUE sep) {
  return rb_tr_up_send1_join(array, sep);
}

VALUE rb_ary_to_s(VALUE array) {
  return rb_tr_up_send0_to_s(array);
}

VALUE rb_ary_reverse(VALUE array) {
  return rb_tr_up_send0_reverse_bang(array);
}

VALUE rb_ary_shift(VALUE array) {
  return rb_tr_up_send0_shift(array);
}

VALUE rb_ary_concat(VALUE a, VALUE b) {
  return rb_tr_up_send1_concat(a, b);
}

VALUE rb_ary_plus(VALUE a, VALUE b) {
  return rb_tr_up_send1_plus(a, b);
}

VALUE rb_ary_to_ary(VALUE array) {
  VALUE tmp = rb_check_array_type(array);

  if (!NIL_P(tmp)) return tmp;
  return rb_ary_new_from_args(1, array);
}

VALUE rb_ary_subseq(VALUE array, long start, long length) {
  return rb_tr_up_send2_aref(array, LONG2NUM(start), LONG2NUM(length));
}

VALUE rb_ary_cat(VALUE array, const VALUE *cat, long n) {
  return rb_tr_up_send1_concat(array, rb_ary_new4(n, cat));
}

VALUE rb_ary_rotate(VALUE array, long n) {
  if (n != 0) {
    return rb_tr_up_send1_rotate_bang(array, LONG2NUM(n));
  }
  return Qnil;
}

// NOTE: #define rb_ary_tmp_new rb_ary_hidden_new in array.h
VALUE rb_ary_hidden_new(long capa) {
  return rb_ary_new_capa(capa);
}

VALUE rb_ary_freeze(VALUE array) {
  return rb_obj_freeze(array);
}

VALUE rb_ary_dup(VALUE array) {
  return rb_obj_dup(array);
}
