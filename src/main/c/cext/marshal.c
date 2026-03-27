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

// Marshal, rb_marshal_*

VALUE rb_marshal_dump(VALUE obj, VALUE port) {
  return RUBY_CEXT_INVOKE("rb_marshal_dump", obj, port);
}

VALUE rb_marshal_load(VALUE port) {
  return RUBY_CEXT_INVOKE("rb_marshal_load", port);
}
