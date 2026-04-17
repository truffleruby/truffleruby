#include "rbs/string.h"

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <ctype.h>

rbs_string_t rbs_string_new(const char *start, const char *end) {
    return (rbs_string_t) {
        .start = start,
        .end = end,
    };
}

rbs_string_t rbs_string_strip_whitespace(rbs_string_t *self) {
    const char *new_start = self->start;
    while (isspace(*new_start) && new_start < self->end) {
        new_start++;
    }

    if (new_start == self->end) { // Handle empty string case
        return rbs_string_new(new_start, new_start);
    }

    const char *new_end = self->end - 1;
    while (isspace(*new_end) && new_start < new_end) {
        new_end--;
    }

    return rbs_string_new(new_start, new_end + 1);
}

size_t rbs_string_len(const rbs_string_t self) {
    return self.end - self.start;
}

bool rbs_string_equal(const rbs_string_t lhs, const rbs_string_t rhs) {
    if (lhs.start == rhs.start && lhs.end == rhs.end) return true;
    if (rbs_string_len(lhs) != rbs_string_len(rhs)) return false;
    return strncmp(lhs.start, rhs.start, rbs_string_len(lhs)) == 0;
}
