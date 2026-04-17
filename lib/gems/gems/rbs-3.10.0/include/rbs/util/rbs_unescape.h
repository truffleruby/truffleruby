#ifndef RBS_RBS_UNESCAPE_H
#define RBS_RBS_UNESCAPE_H

#include <stddef.h>
#include "rbs/util/rbs_allocator.h"
#include "rbs/string.h"
#include "rbs/util/rbs_encoding.h"

/**
 * Receives `rbs_parser_t` and `range`, which represents a string token or symbol token, and returns a string VALUE.
 *
 *    Input token | Output string
 *    ------------+-------------
 *    "foo\\n"    | foo\n
 *    'foo'       | foo
 *    `bar`       | bar
 *    :"baz\\t"   | baz\t
 *    :'baz'      | baz
 *
 * @returns A new owned string that will be freed when the allocator is freed.
 * */
rbs_string_t rbs_unquote_string(rbs_allocator_t *, const rbs_string_t input, const rbs_encoding_t *encoding);

#endif // RBS_RBS_UNESCAPE_H
