#ifndef INTERNAL_HASH_H                                  /*-*-C-*-vi:se ft=c:*/
#define INTERNAL_HASH_H

#include "ruby.h"

// Used by st.c
VALUE rb_hash_key_str(VALUE);
// Has C API specs
VALUE rb_ident_hash_new(void);

#endif /* INTERNAL_HASH_H */
