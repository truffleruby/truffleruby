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

// Enumerable and Enumerator, rb_enum*

VALUE rb_range_component_beg_len(VALUE b, VALUE e, int excl, long *begp, long *lenp, long len, int err);

VALUE rb_enumeratorize(VALUE obj, VALUE meth, int argc, const VALUE *argv) {
  return RUBY_CEXT_INVOKE("rb_enumeratorize", obj, meth, rb_ary_new4(argc, argv));
}

#undef rb_enumeratorize_with_size
VALUE rb_enumeratorize_with_size(VALUE obj, VALUE meth, int argc, const VALUE *argv, rb_enumerator_size_func *size_fn) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_enumeratorize_with_size", rb_tr_unwrap(obj), rb_tr_unwrap(meth), rb_tr_unwrap(rb_ary_new4(argc, argv)), size_fn));
}

int rb_arithmetic_sequence_extract(VALUE obj, rb_arithmetic_sequence_components_t *component) {
    if (rb_obj_is_kind_of(obj, rb_cArithSeq)) {
        component->begin       = rb_ivar_get(obj, rb_intern("@begin"));
        component->end         = rb_ivar_get(obj, rb_intern("@end"));
        component->step        = rb_ivar_get(obj, rb_intern("@step"));
        component->exclude_end = rb_ivar_get(obj, rb_intern("@exclude_end"));
        return 1;
    } else if (rb_range_values(obj, &component->begin, &component->end, &component->exclude_end)) {
        component->step  = INT2FIX(1);
        return 1;
    }

    return 0;
}

VALUE rb_arithmetic_sequence_beg_len_step(VALUE obj, long *begp, long *lenp, long *stepp, long len, int err) {
    RBIMPL_NONNULL_ARG(begp);
    RBIMPL_NONNULL_ARG(lenp);
    RBIMPL_NONNULL_ARG(stepp);

    rb_arithmetic_sequence_components_t aseq;
    if (!rb_arithmetic_sequence_extract(obj, &aseq)) {
        return Qfalse;
    }

    long step = NIL_P(aseq.step) ? 1 : NUM2LONG(aseq.step);
    *stepp = step;

    if (step < 0) {
        if (aseq.exclude_end && !NIL_P(aseq.end)) {
            /* Handle exclusion before range reversal */
            aseq.end = LONG2NUM(NUM2LONG(aseq.end) + 1);

            /* Don't exclude the previous beginning */
            aseq.exclude_end = 0;
        }
        VALUE tmp = aseq.begin;
        aseq.begin = aseq.end;
        aseq.end = tmp;
    }

    if (err == 0 && (step < -1 || step > 1)) {
        if (rb_range_component_beg_len(aseq.begin, aseq.end, aseq.exclude_end, begp, lenp, len, 1) == Qtrue) {
            if (*begp > len) {
                goto out_of_range;
            }
            if (*lenp > len) {
                goto out_of_range;
            }
            return Qtrue;
        }
    } else {
        return rb_range_component_beg_len(aseq.begin, aseq.end, aseq.exclude_end, begp, lenp, len, err);
    }

  out_of_range:
    rb_raise(rb_eRangeError, "%+"PRIsVALUE" out of range", obj);
    return Qnil;
}