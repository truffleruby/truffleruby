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
#include <ruby/encoding.h>

// Symbol and ID, rb_sym*, rb_id*

bool rb_tr_symbol_p(VALUE obj) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("SYMBOL_P", obj));
}

static VALUE string_for_symbol(VALUE name) {
  if (!RB_TYPE_P(name, T_STRING)) {
    VALUE tmp = rb_check_string_type(name);
    if (NIL_P(tmp)) {
      rb_raise(rb_eTypeError, "%+"PRIsVALUE" is not a symbol", name);
    }
    name = tmp;
  }
  return name;
}

ID rb_to_id(VALUE name) {
  if (SYMBOL_P(name)) {
    return SYM2ID(name);
  }
  name = string_for_symbol(name);
  return rb_intern_str(name);
}

#undef rb_intern
ID rb_intern(const char *string) {
  return rb_intern2(string, strlen(string));
}

ID rb_intern2(const char *string, long length) {
  return SYM2ID(RUBY_CEXT_INVOKE("rb_intern", rb_tr_temporary_native_string(string, length, rb_ascii8bit_encoding())));
}

ID rb_intern3(const char *name, long len, rb_encoding *enc) {
  return SYM2ID(RUBY_CEXT_INVOKE("rb_intern", rb_tr_temporary_native_string(name, len, enc)));
}

VALUE rb_sym2str(VALUE string) {
  return RUBY_INVOKE(string, "name");
}

const char *rb_id2name(ID id) {
  if (id == 0) {
    return NULL; // like CRuby
  }

  VALUE str = rb_id2str(id);
  return RSTRING_PTR(str);
}

VALUE rb_id2str(ID id) {
  if (id == 0) {
    // CRuby returns (VALUE) 0 in that case, see get_id_serial_entry().
    return Qfalse;
  }

  return RUBY_CEXT_INVOKE("rb_id2str", ID2SYM(id));
}

ID rb_id_attrset(ID id) {
  VALUE sym = ID2SYM(id);
  return SYM2ID(RUBY_CEXT_INVOKE("rb_id_attrset", ID2SYM(id)));
}

int rb_is_class_id(ID id) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("rb_is_class_id", ID2SYM(id)));
}

int rb_is_const_id(ID id) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("rb_is_const_id", ID2SYM(id)));
}

int rb_is_instance_id(ID id) {
  return polyglot_as_boolean(RUBY_CEXT_INVOKE_NO_WRAP("rb_is_instance_id", ID2SYM(id)));
}

ID rb_check_id(volatile VALUE *namep) {
  VALUE name = *namep;
  return SYM2ID(name);
}

VALUE rb_check_symbol_cstr(const char *ptr, long len, rb_encoding *enc) {
  return RUBY_CEXT_INVOKE("rb_check_symbol_cstr", rb_tr_temporary_native_string(ptr, len, enc));
}

VALUE rb_sym_to_s(VALUE sym) {
  return RUBY_INVOKE(sym, "to_s");
}

// TODO: rb_tr_sym2id() has a single call site since native cexts, the one below, so the inline cache in it is global.
ID rb_sym2id(VALUE sym) {
  return rb_tr_sym2id(sym);
}

#undef rb_id2sym
VALUE rb_id2sym(ID x) {
  return rb_tr_wrap(rb_tr_id2sym(x));
}

VALUE rb_to_symbol(VALUE name) {
  return RUBY_CEXT_INVOKE("rb_to_symbol", name);
}
