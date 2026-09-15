#include "ruby.h"
#include "ruby/ractor.h"
#include "rubyspec.h"

#ifdef __cplusplus
extern "C" {
#endif

static rb_ractor_local_key_t spec_ractor_key;

static VALUE spec_ractor_local_storage_value_set(VALUE self, VALUE value) {
  rb_ractor_local_storage_value_set(spec_ractor_key, value);
  return value;
}

static VALUE spec_ractor_local_storage_value(VALUE self) {
  return rb_ractor_local_storage_value(spec_ractor_key);
}

static VALUE spec_ractor_local_storage_value_lookup(VALUE self) {
  VALUE value = Qundef;
  if (rb_ractor_local_storage_value_lookup(spec_ractor_key, &value)) {
    return value;
  }
  return Qnil;
}

/* Uses a brand new key so the "no value stored" cases do not depend on spec
 * ordering. */
static rb_ractor_local_key_t fresh_key;

static VALUE spec_fresh_ractor_local_storage_value(VALUE self) {
  return rb_ractor_local_storage_value(fresh_key);
}

static VALUE spec_fresh_ractor_local_storage_value_lookup(VALUE self) {
  VALUE value = Qundef;
  if (rb_ractor_local_storage_value_lookup(fresh_key, &value)) {
    return value;
  }
  return Qnil;
}

void Init_ractor_spec(void) {
  spec_ractor_key = rb_ractor_local_storage_value_newkey();
  fresh_key = rb_ractor_local_storage_value_newkey();

  VALUE rb_mCApiRactorSpecs = rb_define_class("CApiRactorSpecs", rb_cObject);
  rb_define_method(rb_mCApiRactorSpecs, "ractor_local_storage_value_set", spec_ractor_local_storage_value_set, 1);
  rb_define_method(rb_mCApiRactorSpecs, "ractor_local_storage_value", spec_ractor_local_storage_value, 0);
  rb_define_method(rb_mCApiRactorSpecs, "ractor_local_storage_value_lookup", spec_ractor_local_storage_value_lookup, 0);
  rb_define_method(rb_mCApiRactorSpecs, "fresh_ractor_local_storage_value", spec_fresh_ractor_local_storage_value, 0);
  rb_define_method(rb_mCApiRactorSpecs, "fresh_ractor_local_storage_value_lookup", spec_fresh_ractor_local_storage_value_lookup, 0);
}

#ifdef __cplusplus
}
#endif
