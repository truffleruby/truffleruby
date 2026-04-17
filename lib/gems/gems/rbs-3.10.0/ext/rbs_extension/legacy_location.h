#ifndef RBS_LOCATION_H
#define RBS_LOCATION_H

#include "compat.h"

SUPPRESS_RUBY_HEADER_DIAGNOSTICS_BEGIN
#include "ruby.h"
SUPPRESS_RUBY_HEADER_DIAGNOSTICS_END

#include "rbs.h"

/**
 * RBS::Location class
 * */
extern VALUE RBS_Location;

typedef struct {
    VALUE buffer;
    rbs_loc_range rg;
    rbs_loc_children *children; // NULL when no children is allocated
} rbs_loc;

/**
 * Returns new RBS::Location object, with given buffer and range.
 * */
VALUE rbs_new_location(VALUE buffer, rbs_range_t rg);

/**
 * Return rbs_loc associated with the RBS::Location object.
 * */
rbs_loc *rbs_check_location(VALUE location);

/**
 * Allocate memory for child locations.
 *
 * Do not call twice for the same location.
 * */
void rbs_loc_legacy_alloc_children(rbs_loc *loc, unsigned short cap);

/**
 * Define RBS::Location class.
 * */
void rbs__init_location();

#endif
