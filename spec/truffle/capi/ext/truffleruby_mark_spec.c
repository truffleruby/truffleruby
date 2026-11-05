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

#include "ruby.h"
#include "rubyspec.h"

#ifdef __cplusplus
extern "C" {
#endif

// More values than fit in the initial mark buffer, so marking needs to grow it
#define MARKED_VALUES_COUNT 200

enum mark_function {
  MARK_WITH_RB_GC_MARK,
  MARK_WITH_RB_GC_MARK_MOVABLE,
  MARK_WITH_RB_GC_MARK_MAYBE,
  MARK_WITH_RB_GC_MARK_LOCATIONS
};

struct marked_values {
  VALUE values[MARKED_VALUES_COUNT];
  // Another marked_values object, whose mark function runs while this one is running
  VALUE nested;
  VALUE spec;
  enum mark_function mark_with;
};

static int marking_depth = 0;
static int nested_marking_observed = 0;

static void mark_values(struct marked_values *marked, int from, int to) {
  if (marked->mark_with == MARK_WITH_RB_GC_MARK_LOCATIONS) {
    rb_gc_mark_locations(marked->values + from, marked->values + to);
    return;
  }

  for (int i = from; i < to; i++) {
    switch (marked->mark_with) {
      case MARK_WITH_RB_GC_MARK_MOVABLE:
        rb_gc_mark_movable(marked->values[i]);
        break;
      case MARK_WITH_RB_GC_MARK_MAYBE:
        rb_gc_mark_maybe(marked->values[i]);
        break;
      default:
        rb_gc_mark(marked->values[i]);
        break;
    }
  }
}

static void marked_values_mark(void *ptr) {
  struct marked_values *marked = ptr;
  marking_depth++;
  if (marking_depth > 1) {
    nested_marking_observed = 1;
  }

  mark_values(marked, 0, MARKED_VALUES_COUNT / 2);
  if (!NIL_P(marked->nested)) {
    rb_gc_mark(marked->nested);
    // Accessing the data of the nested object in a C extension call runs its mark function when that call returns
    rb_funcall(marked->spec, rb_intern("access_data"), 1, marked->nested);
  }
  mark_values(marked, MARKED_VALUES_COUNT / 2, MARKED_VALUES_COUNT);

  marking_depth--;
}

static const rb_data_type_t marked_values_type = {
  "truffleruby_mark_spec_marked_values",
  { marked_values_mark, RUBY_TYPED_DEFAULT_FREE, NULL },
  NULL, NULL, 0
};

static enum mark_function mark_function_from_name(VALUE name) {
  if (NIL_P(name)) {
    return MARK_WITH_RB_GC_MARK;
  }

  ID id = SYM2ID(name);
  if (id == rb_intern("rb_gc_mark")) {
    return MARK_WITH_RB_GC_MARK;
  } else if (id == rb_intern("rb_gc_mark_movable")) {
    return MARK_WITH_RB_GC_MARK_MOVABLE;
  } else if (id == rb_intern("rb_gc_mark_maybe")) {
    return MARK_WITH_RB_GC_MARK_MAYBE;
  } else if (id == rb_intern("rb_gc_mark_locations")) {
    return MARK_WITH_RB_GC_MARK_LOCATIONS;
  } else {
    rb_raise(rb_eArgError, "unknown mark function: %"PRIsVALUE, name);
  }
}

// The created Strings are only referenced from the struct, so only the mark function keeps them alive
static VALUE mark_spec_new_marked_values(int argc, VALUE *argv, VALUE self) {
  VALUE prefix, nested, mark_with;
  rb_scan_args(argc, argv, "12", &prefix, &nested, &mark_with);
  enum mark_function mark_function = mark_function_from_name(mark_with);

  struct marked_values *marked;
  VALUE object = TypedData_Make_Struct(rb_cObject, struct marked_values, &marked_values_type, marked);
  for (int i = 0; i < MARKED_VALUES_COUNT; i++) {
    marked->values[i] = rb_sprintf("%"PRIsVALUE" %d", prefix, i);
  }
  marked->nested = nested;
  marked->spec = self;
  marked->mark_with = mark_function;
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

static VALUE mark_spec_mark_outside_of_mark_function(VALUE self, VALUE value) {
  rb_gc_mark(value);
  rb_gc_mark_movable(value);
  rb_gc_mark_maybe(value);
  rb_gc_mark_locations(&value, &value + 1);
  return Qtrue;
}

void Init_truffleruby_mark_spec(void) {
  VALUE cls = rb_define_class("CApiTruffleRubyMarkSpecs", rb_cObject);
  rb_define_method(cls, "new_marked_values", mark_spec_new_marked_values, -1);
  rb_define_method(cls, "access_data", mark_spec_access_data, 1);
  rb_define_method(cls, "marked_values", mark_spec_marked_values, 1);
  rb_define_method(cls, "nested_marking_observed?", mark_spec_nested_marking_observed, 0);
  rb_define_method(cls, "mark_outside_of_mark_function", mark_spec_mark_outside_of_mark_function, 1);
}

#ifdef __cplusplus
}
#endif
