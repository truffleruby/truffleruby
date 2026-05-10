#ifndef RBS__RBS_LOCATION_H
#define RBS__RBS_LOCATION_H

#include "lexer.h"

#include "rbs/util/rbs_constant_pool.h"
#include "rbs/util/rbs_allocator.h"

typedef struct {
    int start;
    int end;
} rbs_loc_range;

typedef struct {
    rbs_constant_id_t name;
    rbs_loc_range rg;
} rbs_loc_entry;

typedef unsigned int rbs_loc_entry_bitmap;

// The flexible array always allocates, but it's okay.
// This struct is not allocated when the `rbs_loc` doesn't have children.
typedef struct {
    unsigned short len;
    unsigned short cap;
    rbs_loc_entry_bitmap required_p;
    rbs_loc_entry entries[1];
} rbs_loc_children;

typedef struct rbs_location {
    rbs_range_t rg;
    rbs_loc_children *children;
} rbs_location_t;

typedef struct rbs_location_list_node {
    rbs_location_t *loc;
    struct rbs_location_list_node *next;
} rbs_location_list_node_t;

typedef struct rbs_location_list {
    rbs_allocator_t *allocator;
    rbs_location_list_node_t *head;
    rbs_location_list_node_t *tail;
    size_t length;
} rbs_location_list_t;

void rbs_loc_alloc_children(rbs_allocator_t *, rbs_location_t *loc, size_t capacity);
void rbs_loc_add_required_child(rbs_location_t *loc, rbs_constant_id_t name, rbs_range_t r);
void rbs_loc_add_optional_child(rbs_location_t *loc, rbs_constant_id_t name, rbs_range_t r);

/**
 * Allocate new rbs_location_t object through the given allocator.
 * */
rbs_location_t *rbs_location_new(rbs_allocator_t *, rbs_range_t rg);

rbs_location_list_t *rbs_location_list_new(rbs_allocator_t *allocator);
void rbs_location_list_append(rbs_location_list_t *list, rbs_location_t *loc);

#endif
