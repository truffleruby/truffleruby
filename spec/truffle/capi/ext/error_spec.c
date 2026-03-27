/*
 * Copyright (c) 2026 TruffleRuby contributors.
 * Copyright (c) 2022-2025 Oracle and/or its affiliates.
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

bool rb_warning_category_enabled_p(rb_warning_category_t category);

static VALUE error_spec_rb_warning_category_enabled_p(VALUE self, VALUE category) {
    bool result = rb_warning_category_enabled_p(FIX2INT(category));
    return result ? Qtrue : Qfalse;
}

static VALUE error_spec_rb_warning_category_enabled_p_deprecated(VALUE self) {
    bool result = rb_warning_category_enabled_p(RB_WARN_CATEGORY_DEPRECATED);
    return result ? Qtrue : Qfalse;
}

static VALUE error_spec_rb_warning_category_enabled_p_experimental(VALUE self) {
    bool result = rb_warning_category_enabled_p(RB_WARN_CATEGORY_EXPERIMENTAL);
    return result ? Qtrue : Qfalse;
}

void Init_error_spec(void) {
    VALUE cls = rb_define_class("CApiErrorSpecs", rb_cObject);
    rb_define_method(cls, "rb_warning_category_enabled_p", error_spec_rb_warning_category_enabled_p, 1);
    rb_define_method(cls, "rb_warning_category_enabled_p_deprecated", error_spec_rb_warning_category_enabled_p_deprecated, 0);
    rb_define_method(cls, "rb_warning_category_enabled_p_experimental", error_spec_rb_warning_category_enabled_p_experimental, 0);
}

#ifdef __cplusplus
}
#endif
