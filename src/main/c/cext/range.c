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

// Range, rb_range_*

VALUE rb_range_new(VALUE beg, VALUE end, int exclude_end) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_range_new", rb_tr_unwrap(beg), rb_tr_unwrap(end), exclude_end));
}

/* This function can not be inlined as the two rb_intern macros
   generate static variables, and would produce unwanted
   warnings. This does mean that the start and end VALUEs will be
   converted to native handles and back if Sulong doesn't choose to
   inline this function, but this is unlikely to cause a major
   performance issue.
 */
int rb_range_values(VALUE range, VALUE *begp, VALUE *endp, int *exclp) {
  if (!rb_obj_is_kind_of(range, rb_cRange)) {
    if (!rb_respond_to(range, rb_intern("begin"))) return Qfalse;
    if (!rb_respond_to(range, rb_intern("end"))) return Qfalse;
  }

  *begp = RUBY_INVOKE(range, "begin");
  *endp = RUBY_INVOKE(range, "end");
  *exclp = (int) RTEST(RUBY_INVOKE(range, "exclude_end?"));
  return Qtrue;
}

// NOTE: this is not a public API function but a helper function from CRuby

/* Extract the components of a Range.
 *
 * You can use +err+ to control the behavior of out-of-range and exception.
 *
 * When +err+ is 0 or 2, if the begin offset is greater than +len+,
 * it is out-of-range.  The +RangeError+ is raised only if +err+ is 2,
 * in this case.  If +err+ is 0, +Qnil+ will be returned.
 *
 * When +err+ is 1, the begin and end offsets won't be adjusted even if they
 * are greater than +len+.  It allows +rb_ary_aset+ extends arrays.
 *
 * If the begin component of the given range is negative and is too-large
 * abstract value, the +RangeError+ is raised only +err+ is 1 or 2.
 *
 * The case of <code>err = 0</code> is used in item accessing methods such as
 * +rb_ary_aref+, +rb_ary_slice_bang+, and +rb_str_aref+.
 *
 * The case of <code>err = 1</code> is used in Array's methods such as
 * +rb_ary_aset+ and +rb_ary_fill+.
 *
 * The case of <code>err = 2</code> is used in +rb_str_aset+.
 */
VALUE rb_range_component_beg_len(VALUE b, VALUE e, int excl, long *begp, long *lenp, long len, int err) {
    long beg, end;

    beg = NIL_P(b) ? 0 : NUM2LONG(b);
    end = NIL_P(e) ? -1 : NUM2LONG(e);
    if (NIL_P(e)) {
        excl = 0;
    }

    if (beg < 0) {
        beg += len;
        if (beg < 0) {
            goto out_of_range;
        }
    }
    if (end < 0) {
        end += len;
    }
    if (!excl) {
        end++;         /* include end point */
    }
    if (err == 0 || err == 2) {
        if (beg > len) {
            goto out_of_range;
        }
        if (end > len) {
            end = len;
        }
    }
    len = end - beg;
    if (len < 0) {
        len = 0;
    }

    *begp = beg;
    *lenp = len;
    return Qtrue;

  out_of_range:
    return Qnil;
}

VALUE rb_range_beg_len(VALUE range, long *begp, long *lenp, long len, int err) {
  long beg, end, origbeg, origend;
  VALUE b, e;
  int excl;

  if (!rb_range_values(range, &b, &e, &excl)) {
    return Qfalse;
  }

  beg = NUM2LONG(b);
  end = NUM2LONG(e);
  origbeg = beg;
  origend = end;
  if (beg < 0) {
    beg += len;
    if (beg < 0) {
      goto out_of_range;
    }
  }
  if (end < 0) {
    end += len;
  }
  if (!excl) {
    end++;                        /* include end point */
  }
  if (err == 0 || err == 2) {
    if (beg > len) {
      goto out_of_range;
    }
    if (end > len) {
      end = len;
    }
  }
  len = end - beg;
  if (len < 0) {
    len = 0;
  }

  *begp = beg;
  *lenp = len;
  return Qtrue;

out_of_range:
  if (err) {
    rb_raise(rb_eRangeError, "%ld..%s%ld out of range",
             origbeg, excl ? "." : "", origend);
  }
  return Qnil;
}
