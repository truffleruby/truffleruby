/* This is a public domain general purpose hash table package
   originally written by Peter Moore @ UCB.

   The hash table data structures were redesigned and the package was
   rewritten by Vladimir Makarov <vmakarov@redhat.com>.  */

#ifndef RUBY_ST2_H
#define RUBY_ST2_H 1

// TruffleRuby: the concept of "parser_st.{h.c}" file is a big hack, so instead we define the bare minimum to reuse st functions as-is

#if defined(__cplusplus)
extern "C" {
#if 0
} /* satisfy cc-mode */
#endif
#endif

#include "ruby/defines.h"
#include "ruby/st.h"

RUBY_SYMBOL_EXPORT_BEGIN

#define rb_parser_st_locale_insensitive_strcasecmp rb_st_locale_insensitive_strcasecmp
#define rb_parser_st_locale_insensitive_strncasecmp rb_st_locale_insensitive_strncasecmp

RUBY_SYMBOL_EXPORT_END

#if defined(__cplusplus)
#if 0
{ /* satisfy cc-mode */
#endif
}  /* extern "C" { */
#endif

#endif /* RUBY_ST2_H */