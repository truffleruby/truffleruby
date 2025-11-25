#include "rbs_extension.h"

VALUE rbs_unquote_string(parserstate *state, range rg, int offset_bytes) {
  VALUE string = state->lexstate->string;
  rb_encoding *enc = rb_enc_get(string);

  unsigned int first_char = rb_enc_mbc_to_codepoint(
    RSTRING_PTR(string) + rg.start.byte_pos + offset_bytes,
    RSTRING_END(string),
    enc
  );

  int byte_length = rg.end.byte_pos - rg.start.byte_pos - offset_bytes;

  if (first_char == '"' || first_char == '\'' || first_char == '`') {
    int bs = rb_enc_codelen(first_char, enc);
    offset_bytes += bs;
    byte_length -= 2 * bs;
  }

  char *buffer = RSTRING_PTR(state->lexstate->string) + rg.start.byte_pos + offset_bytes;
  VALUE str = rb_enc_str_new(buffer, byte_length, enc);

  return rb_funcall(
    RBS_Types_Literal,
    rb_intern("unescape_string"),
    2,
    str,
    first_char == '\"' ? Qtrue : Qfalse
  );
}

