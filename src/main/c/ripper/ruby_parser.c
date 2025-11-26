#include "internal/ruby_parser.h"
#include "internal/string.h"

// A subset of ruby_parser.c from CRuby, necessary for Ripper

VALUE rb_str_new_parser_string(rb_parser_string_t *str) {
    VALUE string = rb_enc_literal_str(str->ptr, str->len, str->enc);
    rb_enc_str_coderange(string);
    return string;
}

VALUE
rb_str_new_mutable_parser_string(rb_parser_string_t *str)
{
    return rb_enc_str_new(str->ptr, str->len, str->enc);
}
