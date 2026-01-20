/*
 * Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */

#include "ruby.h"
#include "rubyspec.h"
#include "internal_id.h"

#ifdef __cplusplus
extern "C" {
#endif

static VALUE symbol_id_spec_ID2SYM(VALUE self, VALUE str) {
  char *name = StringValueCStr(str);
  if (strcmp(name, "idPLUS") == 0){
    return ID2SYM(idPLUS);
  } else if(strcmp(name, "-") == 0) {
    return ID2SYM('-');
  } else if(strcmp(name, "idLTLT") == 0) {
    return ID2SYM(idLTLT);
  } else if (strcmp(name, "idEmptyP") == 0) {
    return ID2SYM(idEmptyP);
  } else if (strcmp(name, "idMesg") == 0) {
    return ID2SYM(idMesg);
  } else if (strcmp(name, "idBACKREF") == 0) {
     return ID2SYM(idBACKREF);
  } else {
    return Qnil;
  }
}

static VALUE symbol_id_spec_SYM2ID(VALUE self, VALUE sym, VALUE str) {
  ID id = SYM2ID(sym);
  char *name = StringValueCStr(str);
  if (strcmp(name, "idPLUS") == 0){
    return SYM2ID(sym) == idPLUS ? Qtrue : Qfalse;
  } else if(strcmp(name, "-") == 0) {
    return SYM2ID(sym) == '-' ? Qtrue : Qfalse;
  } else if(strcmp(name, "idLTLT") == 0) {
    return SYM2ID(sym) == idLTLT ? Qtrue : Qfalse;
  } else if (strcmp(name, "idEmptyP") == 0) {
    return SYM2ID(sym) == idEmptyP ? Qtrue : Qfalse;
  } else if (strcmp(name, "idMesg") == 0) {
    return SYM2ID(sym) == idMesg ? Qtrue : Qfalse;
  } else if (strcmp(name, "idBACKREF") == 0) {
    return SYM2ID(sym) == idBACKREF ? Qtrue : Qfalse;
  } else {
    return Qnil;
  }
}

static VALUE symbol_id_spec_ID2SYM_SYM2ID(VALUE self, VALUE str) {
  char *name = StringValueCStr(str);
  if (strcmp(name, "idPLUS") == 0){
    return SYM2ID(ID2SYM(idPLUS)) == idPLUS ? Qtrue : Qfalse;
  } else if(strcmp(name, "-") == 0) {
    return SYM2ID(ID2SYM('-')) == '-' ? Qtrue : Qfalse;
  } else if(strcmp(name, "idLTLT") == 0) {
    return SYM2ID(ID2SYM(idLTLT)) == idLTLT ? Qtrue : Qfalse ;
  } else if (strcmp(name, "idEmptyP") == 0) {
    return SYM2ID(ID2SYM(idEmptyP)) == idEmptyP ? Qtrue : Qfalse;
  } else if (strcmp(name, "idMesg") == 0) {
    return SYM2ID(ID2SYM(idMesg)) == idMesg ? Qtrue : Qfalse;
  } else if (strcmp(name, "idBACKREF") == 0) {
     return SYM2ID(ID2SYM(idBACKREF)) == idBACKREF ? Qtrue : Qfalse;
  } else {
    return Qfalse;
  }
}

static VALUE symbol_id_spec_SYM2ID_ID2SYM(VALUE self, VALUE sym) {
  return ID2SYM(SYM2ID(sym));
}

void Init_truffleruby_symbol_id_spec(void) {
  VALUE cls = rb_define_class("CApiTruffleRubySymbolIDSpecs", rb_cObject);
  rb_define_method(cls, "ID2SYM", symbol_id_spec_ID2SYM, 1);
  rb_define_method(cls, "SYM2ID", symbol_id_spec_SYM2ID, 2);
  rb_define_method(cls, "ID2SYM_SYM2ID", symbol_id_spec_ID2SYM_SYM2ID, 1);
  rb_define_method(cls, "SYM2ID_ID2SYM", symbol_id_spec_SYM2ID_ID2SYM, 1);
}

#ifdef __cplusplus
}
#endif