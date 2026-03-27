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

VALUE rb_tr_warning_category_to_name(rb_warning_category_t category) {
    VALUE category_name;

    switch (category) {
        case RB_WARN_CATEGORY_DEPRECATED:
            category_name = ID2SYM(rb_intern("deprecated"));
            break;
        case RB_WARN_CATEGORY_EXPERIMENTAL:
            category_name = ID2SYM(rb_intern("experimental"));
            break;
        default:
            category_name = Qnil;
    }

    return category_name;
}

void rb_tr_category_warn_va_list(rb_warning_category_t category, const char *fmt, va_list args) {
    VALUE message, category_name;

    message = rb_vsprintf(fmt, args);
    category_name = rb_tr_warning_category_to_name(category);
    RUBY_CEXT_INVOKE("rb_tr_warn_category", message, category_name);
}

bool rb_warning_category_enabled_p(rb_warning_category_t category) {
    VALUE category_name;

    category_name = rb_tr_warning_category_to_name(category);

    if (category_name != Qnil) {
        return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("rb_warning_category_enabled_p", category_name));
    } else {
        return true;
    }
}

void rb_tr_warn_va_list(const char *fmt, va_list args) {
  RUBY_CEXT_INVOKE("rb_tr_warn", rb_vsprintf(fmt, args));
}
