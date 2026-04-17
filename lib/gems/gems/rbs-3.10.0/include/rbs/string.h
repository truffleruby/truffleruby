#ifndef RBS__RBS_STRING_H
#define RBS__RBS_STRING_H

#include <stddef.h>
#include <stdbool.h>
#include "rbs/util/rbs_allocator.h"

typedef struct {
    const char *start;
    const char *end;
} rbs_string_t;

#define RBS_STRING_NULL ((rbs_string_t) { \
    .start = NULL,                        \
    .end = NULL,                          \
})

/**
 * Returns a new `rbs_string_t` struct
 */
rbs_string_t rbs_string_new(const char *start, const char *end);

/**
 * Copies a portion of the input string into a new owned string.
 * @param start_inset Number of characters to exclude from the start
 * @param length Number of characters to include
 * @return A new owned string that will be freed when the allocator is freed.
 */
rbs_string_t rbs_string_copy_slice(rbs_allocator_t *, rbs_string_t *self, size_t start_inset, size_t length);

/**
 * Drops the leading and trailing whitespace from the given string, in-place.
 * @returns A new string that provides a view into the original string `self`.
 */
rbs_string_t rbs_string_strip_whitespace(rbs_string_t *self);

/**
 * Returns the length of the string.
 */
size_t rbs_string_len(const rbs_string_t self);

/**
 * Compares two strings for equality.
 */
bool rbs_string_equal(const rbs_string_t lhs, const rbs_string_t rhs);

#endif
