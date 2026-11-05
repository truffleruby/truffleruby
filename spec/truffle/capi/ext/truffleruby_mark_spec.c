/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

#include "ruby.h"
#include "rubyspec.h"

#ifdef __cplusplus
extern "C" {
#endif

// More values than fit in the initial mark buffer, so marking needs to grow it
#define MARKED_VALUES_COUNT 200

struct marked_values {
  VALUE values[MARKED_VALUES_COUNT];
  // Another marked_values object, whose mark function runs while this one is running
  VALUE nested;
  VALUE spec;
};

static int marking_depth = 0;
static int nested_marking_observed = 0;

static void marked_values_mark(void *ptr) {
  struct marked_values *marked = ptr;
  marking_depth++;
  if (marking_depth > 1) {
    nested_marking_observed = 1;
  }

  for (int i = 0; i < MARKED_VALUES_COUNT / 2; i++) {
    rb_gc_mark(marked->values[i]);
  }
  if (!NIL_P(marked->nested)) {
    rb_gc_mark(marked->nested);
    // Accessing the data of the nested object in a C extension call runs its mark function when that call returns
    rb_funcall(marked->spec, rb_intern("access_data"), 1, marked->nested);
  }
  for (int i = MARKED_VALUES_COUNT / 2; i < MARKED_VALUES_COUNT; i++) {
    rb_gc_mark(marked->values[i]);
  }

  marking_depth--;
}

static const rb_data_type_t marked_values_type = {
  "truffleruby_mark_spec_marked_values",
  { marked_values_mark, RUBY_TYPED_DEFAULT_FREE, NULL },
  NULL, NULL, 0
};

// The created Strings are only referenced from the struct, so only the mark function keeps them alive
static VALUE mark_spec_new_marked_values(int argc, VALUE *argv, VALUE self) {
  VALUE prefix, nested;
  rb_scan_args(argc, argv, "11", &prefix, &nested);
  struct marked_values *marked;
  VALUE object = TypedData_Make_Struct(rb_cObject, struct marked_values, &marked_values_type, marked);
  for (int i = 0; i < MARKED_VALUES_COUNT; i++) {
    marked->values[i] = rb_sprintf("%"PRIsVALUE" %d", prefix, i);
  }
  marked->nested = nested;
  marked->spec = self;
  return object;
}

static VALUE mark_spec_access_data(VALUE self, VALUE object) {
  struct marked_values *marked;
  TypedData_Get_Struct(object, struct marked_values, &marked_values_type, marked);
  return Qnil;
}

static VALUE mark_spec_marked_values(VALUE self, VALUE object) {
  struct marked_values *marked;
  TypedData_Get_Struct(object, struct marked_values, &marked_values_type, marked);
  return rb_ary_new_from_values(MARKED_VALUES_COUNT, marked->values);
}

static VALUE mark_spec_nested_marking_observed(VALUE self) {
  return nested_marking_observed ? Qtrue : Qfalse;
}

void Init_truffleruby_mark_spec(void) {
  VALUE cls = rb_define_class("CApiTruffleRubyMarkSpecs", rb_cObject);
  rb_define_method(cls, "new_marked_values", mark_spec_new_marked_values, -1);
  rb_define_method(cls, "access_data", mark_spec_access_data, 1);
  rb_define_method(cls, "marked_values", mark_spec_marked_values, 1);
  rb_define_method(cls, "nested_marking_observed?", mark_spec_nested_marking_observed, 0);
}

#ifdef __cplusplus
}
#endif
