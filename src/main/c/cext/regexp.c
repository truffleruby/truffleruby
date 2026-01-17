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

// Regexp, rb_reg_*

VALUE rb_backref_get(void) {
  return RUBY_CEXT_INVOKE("rb_backref_get");
}

void rb_backref_set(VALUE str) {
  RUBY_CEXT_INVOKE("rb_backref_set", str);
}

VALUE rb_reg_match_pre(VALUE match) {
  return RUBY_CEXT_INVOKE("rb_reg_match_pre", match);
}

VALUE rb_reg_new(const char *s, long len, int options) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_reg_new", rb_tr_unwrap(rb_str_new(s, len)), options));
}

VALUE rb_reg_compile(VALUE str, int options, const char *sourcefile, int sourceline) {
  // TODO BJF May-29-2020 implement sourcefile, sourceline
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_reg_compile", rb_tr_unwrap(str), options));
}

VALUE rb_reg_new_str(VALUE s, int options) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_reg_new_str", rb_tr_unwrap(s), options));
}

VALUE rb_reg_nth_match(int nth, VALUE match) {
  return rb_tr_wrap(polyglot_invoke(RUBY_CEXT, "rb_reg_nth_match", nth, rb_tr_unwrap(match)));
}

int rb_reg_options(VALUE re) {
  return FIX2INT(RUBY_CEXT_INVOKE("rb_reg_options", re));
}

VALUE rb_reg_regcomp(VALUE str) {
  return RUBY_CEXT_INVOKE("rb_reg_regcomp", str);
}

VALUE rb_reg_match(VALUE re, VALUE str) {
  return RUBY_CEXT_INVOKE("rb_reg_match", re, str);
}

void rb_match_busy(VALUE match) {
// Unclear what this does on MRI
// - https://github.com/ruby/ruby/commit/f1b76ea63ce40670071a857f408a4747c571f1e9
// - https://bugs.ruby-lang.org/issues/16024
}

static const char casetable[] = {
        '\000', '\001', '\002', '\003', '\004', '\005', '\006', '\007',
        '\010', '\011', '\012', '\013', '\014', '\015', '\016', '\017',
        '\020', '\021', '\022', '\023', '\024', '\025', '\026', '\027',
        '\030', '\031', '\032', '\033', '\034', '\035', '\036', '\037',
        /* ' '     '!'     '"'     '#'     '$'     '%'     '&'     ''' */
        '\040', '\041', '\042', '\043', '\044', '\045', '\046', '\047',
        /* '('     ')'     '*'     '+'     ','     '-'     '.'     '/' */
        '\050', '\051', '\052', '\053', '\054', '\055', '\056', '\057',
        /* '0'     '1'     '2'     '3'     '4'     '5'     '6'     '7' */
        '\060', '\061', '\062', '\063', '\064', '\065', '\066', '\067',
        /* '8'     '9'     ':'     ';'     '<'     '='     '>'     '?' */
        '\070', '\071', '\072', '\073', '\074', '\075', '\076', '\077',
        /* '@'     'A'     'B'     'C'     'D'     'E'     'F'     'G' */
        '\100', '\141', '\142', '\143', '\144', '\145', '\146', '\147',
        /* 'H'     'I'     'J'     'K'     'L'     'M'     'N'     'O' */
        '\150', '\151', '\152', '\153', '\154', '\155', '\156', '\157',
        /* 'P'     'Q'     'R'     'S'     'T'     'U'     'V'     'W' */
        '\160', '\161', '\162', '\163', '\164', '\165', '\166', '\167',
        /* 'X'     'Y'     'Z'     '['     '\'     ']'     '^'     '_' */
        '\170', '\171', '\172', '\133', '\134', '\135', '\136', '\137',
        /* '`'     'a'     'b'     'c'     'd'     'e'     'f'     'g' */
        '\140', '\141', '\142', '\143', '\144', '\145', '\146', '\147',
        /* 'h'     'i'     'j'     'k'     'l'     'm'     'n'     'o' */
        '\150', '\151', '\152', '\153', '\154', '\155', '\156', '\157',
        /* 'p'     'q'     'r'     's'     't'     'u'     'v'     'w' */
        '\160', '\161', '\162', '\163', '\164', '\165', '\166', '\167',
        /* 'x'     'y'     'z'     '{'     '|'     '}'     '~' */
        '\170', '\171', '\172', '\173', '\174', '\175', '\176', '\177',
        '\200', '\201', '\202', '\203', '\204', '\205', '\206', '\207',
        '\210', '\211', '\212', '\213', '\214', '\215', '\216', '\217',
        '\220', '\221', '\222', '\223', '\224', '\225', '\226', '\227',
        '\230', '\231', '\232', '\233', '\234', '\235', '\236', '\237',
        '\240', '\241', '\242', '\243', '\244', '\245', '\246', '\247',
        '\250', '\251', '\252', '\253', '\254', '\255', '\256', '\257',
        '\260', '\261', '\262', '\263', '\264', '\265', '\266', '\267',
        '\270', '\271', '\272', '\273', '\274', '\275', '\276', '\277',
        '\300', '\301', '\302', '\303', '\304', '\305', '\306', '\307',
        '\310', '\311', '\312', '\313', '\314', '\315', '\316', '\317',
        '\320', '\321', '\322', '\323', '\324', '\325', '\326', '\327',
        '\330', '\331', '\332', '\333', '\334', '\335', '\336', '\337',
        '\340', '\341', '\342', '\343', '\344', '\345', '\346', '\347',
        '\350', '\351', '\352', '\353', '\354', '\355', '\356', '\357',
        '\360', '\361', '\362', '\363', '\364', '\365', '\366', '\367',
        '\370', '\371', '\372', '\373', '\374', '\375', '\376', '\377',
};

int rb_memcicmp(const void *x, const void *y, long len) {
  const unsigned char *p1 = x, *p2 = y;
  int tmp;

  while (len--) {
    if ((tmp = casetable[(unsigned)*p1++] - casetable[(unsigned)*p2++])) {
      return tmp;
    }
  }
  return 0;
}
