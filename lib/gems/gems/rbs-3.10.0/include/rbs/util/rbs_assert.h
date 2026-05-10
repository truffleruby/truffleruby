#ifndef RBS_ASSERT_H
#define RBS_ASSERT_H

#include "rbs/defines.h"
#include <stdbool.h>

/**
 * RBS_ASSERT macro that calls rbs_assert in debug builds and is removed in release builds.
 * In debug mode, it forwards all arguments to the rbs_assert function.
 * In release mode, it expands to nothing.
 */
#ifdef NDEBUG
#define RBS_ASSERT(condition, ...) ((void) 0)
#else
#define RBS_ASSERT(condition, ...) rbs_assert_impl(condition, __VA_ARGS__)
#endif

void rbs_assert_impl(bool condition, const char *fmt, ...) RBS_ATTRIBUTE_FORMAT(2, 3);

#endif
