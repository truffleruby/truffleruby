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

/* Uses a separate key so the "no value stored" cases do not depend on spec
 * ordering. */
static rb_ractor_local_key_t nonexistent_key;

static VALUE spec_nonexistent_ractor_local_storage_value(VALUE self) {
  return rb_ractor_local_storage_value(nonexistent_key);
}

static VALUE spec_nonexistent_ractor_local_storage_value_lookup(VALUE self) {
  VALUE value = Qundef;
  if (rb_ractor_local_storage_value_lookup(nonexistent_key, &value)) {
    return value;
  }
  return Qnil;
}

void Init_ractor_spec(void) {
  /* Lets the methods run without the C extension lock on implementations
   * having one, so concurrent accesses to a key are really concurrent. */
  rb_ext_ractor_safe(true);

  spec_ractor_key = rb_ractor_local_storage_value_newkey();
  nonexistent_key = rb_ractor_local_storage_value_newkey();

  VALUE rb_mCApiRactorSpecs = rb_define_class("CApiRactorSpecs", rb_cObject);
  rb_define_method(rb_mCApiRactorSpecs, "ractor_local_storage_value_set", spec_ractor_local_storage_value_set, 1);
  rb_define_method(rb_mCApiRactorSpecs, "ractor_local_storage_value", spec_ractor_local_storage_value, 0);
  rb_define_method(rb_mCApiRactorSpecs, "ractor_local_storage_value_lookup", spec_ractor_local_storage_value_lookup, 0);
  rb_define_method(rb_mCApiRactorSpecs, "nonexistent_ractor_local_storage_value", spec_nonexistent_ractor_local_storage_value, 0);
  rb_define_method(rb_mCApiRactorSpecs, "nonexistent_ractor_local_storage_value_lookup", spec_nonexistent_ractor_local_storage_value_lookup, 0);
}

#ifdef __cplusplus
}
#endif
