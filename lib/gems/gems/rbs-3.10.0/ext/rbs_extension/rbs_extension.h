#include <stdbool.h>
#include "compat.h"

SUPPRESS_RUBY_HEADER_DIAGNOSTICS_BEGIN
#include "ruby.h"
#include "ruby/re.h"
#include "ruby/encoding.h"
SUPPRESS_RUBY_HEADER_DIAGNOSTICS_END

#include "class_constants.h"
#include "rbs.h"

/**
 * RBS::Parser class
 * */
extern VALUE RBS_Parser;
