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

#include <ctype.h> // isdigit

// Parsing Ruby arguments from C functions

static VALUE rb_keyword_error_new(const char *error, VALUE keys) {
  long i = 0, len = RARRAY_LEN(keys);
  VALUE error_message = rb_sprintf("%s keyword%.*s", error, len > 1, "s");

  if (len > 0) {
    rb_str_append(error_message, rb_str_new_cstr(": "));
    while (1) {
      const VALUE k = RARRAY_AREF(keys, i);
      rb_str_append(error_message, rb_inspect(k));
      if (++i >= len) break;
      rb_str_append(error_message, rb_str_new_cstr(", "));
    }
  }

  return rb_exc_new_str(rb_eArgError, error_message);
}

RBIMPL_ATTR_NORETURN()
static void rb_keyword_error(const char *error, VALUE keys) {
  rb_exc_raise(rb_keyword_error_new(error, keys));
}

RBIMPL_ATTR_NORETURN()
static void unknown_keyword_error(VALUE hash, const ID *table, int keywords) {
  int i;
  for (i = 0; i < keywords; i++) {
    VALUE key = table[i];
    rb_hash_delete(hash, ID2SYM(key));
  }
  rb_keyword_error("unknown", RUBY_INVOKE(hash, "keys"));
}

static VALUE rb_tr_extract_keyword(VALUE keyword_hash, ID key, VALUE *values) {
  VALUE keySym = ID2SYM(key);
  VALUE val = rb_hash_lookup2(keyword_hash, keySym, Qundef);
   if (values) {
     rb_hash_delete(keyword_hash, keySym);
   }
   return val;
}

int rb_get_kwargs(VALUE keyword_hash, const ID *table, int required, int optional, VALUE *values) {
  int rest = 0;
  int extracted = 0;
  VALUE missing = Qnil;

  if (optional < 0) {
    rest = 1;
    optional = -1-optional;
  }

  for (int n = 0; n < required; n++) {
    VALUE val = rb_tr_extract_keyword(keyword_hash, table[n], values);
    if (values) {
      values[n] = val;
    }
    if (val == Qundef) {
      if (NIL_P(missing)) {
        missing = rb_ary_new();
      }
      rb_ary_push(missing, ID2SYM(table[n]));
      rb_keyword_error("missing", missing);
    }
    extracted++;
  }

  if (optional && !NIL_P(keyword_hash)) {
    for (int m = required; m < required + optional; m++) {
      VALUE val = rb_tr_extract_keyword(keyword_hash, table[m], values);
      if (values) {
        values[m] = val;
      }
      if (val != Qundef) {
        extracted++;
      }
    }
  }

  if (!rest && !NIL_P(keyword_hash)) {
    if (RHASH_SIZE(keyword_hash) > (unsigned int)(values ? 0 : extracted)) {
      unknown_keyword_error(keyword_hash, table, required + optional);
    }
  }

  for (int i = extracted; i < required + optional; i++) {
    values[i] = Qundef;
  }

  return extracted;
}

void rb_tr_scan_args_kw_parse(const char *format, struct rb_tr_scan_args_parse_data *parse_data) {
  const char *formatp = format;

  if (isdigit(*formatp)) {
    parse_data->pre = *formatp - '0';
    formatp++;

    if (isdigit(*formatp)) {
      parse_data->optional = *formatp - '0';
      formatp++;
    }
  }

  if (*formatp == '*') {
    parse_data->rest = true;
    formatp++;
  } else {
    parse_data->rest = false;
  }

  if (isdigit(*formatp)) {
    parse_data->post = *formatp - '0';
    formatp++;
  }

  if (*formatp == ':') {
    parse_data->kwargs = true;
    formatp++;
  } else {
    parse_data->kwargs = false;
  }

  if (*formatp == '&') {
    parse_data->block = true;
    formatp++;
  } else {
    parse_data->block = false;
  }

  if (*formatp != '\0') {
    rb_raise(rb_eArgError, "bad rb_scan_args format");
  }
}
