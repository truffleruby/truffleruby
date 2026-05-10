#ifndef RBS__RBS_STRING_BRIDGING_H
#define RBS__RBS_STRING_BRIDGING_H

#include "compat.h"

SUPPRESS_RUBY_HEADER_DIAGNOSTICS_BEGIN
#include "ruby.h"
#include "ruby/encoding.h"
SUPPRESS_RUBY_HEADER_DIAGNOSTICS_END

#include "rbs/string.h"

/**
 * @returns A new shared rbs_string_t from the given Ruby string, which points into the given Ruby String's memory,
 * and does not need to be `free()`ed. However, the Ruby String needs to be kept alive for the duration of the rbs_string_t.
 */
rbs_string_t rbs_string_from_ruby_string(VALUE ruby_string);

/**
 * Returns a new Ruby string from the given rbs_string_t.
 */
VALUE rbs_string_to_ruby_string(rbs_string_t *self, rb_encoding *encoding);

#endif
