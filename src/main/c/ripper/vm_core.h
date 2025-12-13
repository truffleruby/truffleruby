#ifndef RUBY_VM_CORE_H
#define RUBY_VM_CORE_H

/* TruffleRuby minimal vm_core for building ripper parse */

#include "node.h"

/* included via method.h */
#include "internal.h"
#include "internal/gc.h"            // Needed for rb_xmalloc_mul_add.
#include "internal/static_assert.h" // Needed for STATIC_ASSERT.

const char *ruby_node_name(int node);

#endif

