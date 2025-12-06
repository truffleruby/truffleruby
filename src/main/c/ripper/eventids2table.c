#include "ruby/ruby.h"

#define intern_sym(name) ID2SYM(rb_intern_const(name))

void
ripper_init_eventids2_table(VALUE self)
{
    VALUE h = rb_hash_new();
    rb_define_const(self, "SCANNER_EVENT_TABLE", h);
    rb_hash_aset(h, intern_sym("CHAR"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("__end__"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("backref"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("backtick"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("comma"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("comment"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("const"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("cvar"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("embdoc"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("embdoc_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("embdoc_end"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("embexpr_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("embexpr_end"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("embvar"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("float"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("gvar"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("heredoc_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("heredoc_end"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("ident"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("ignored_nl"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("imaginary"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("int"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("ivar"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("kw"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("label"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("label_end"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("lbrace"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("lbracket"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("lparen"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("nl"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("op"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("period"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("qsymbols_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("qwords_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("rational"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("rbrace"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("rbracket"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("regexp_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("regexp_end"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("rparen"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("semicolon"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("sp"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("symbeg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("symbols_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("tlambda"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("tlambeg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("tstring_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("tstring_content"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("tstring_end"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("words_beg"), INT2FIX(1));
    rb_hash_aset(h, intern_sym("words_sep"), INT2FIX(1));
}

#define RIPPER_EVENTIDS2_TABLE_SIZE 51
