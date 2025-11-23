#ifndef RUBY_VM_CORE_H
#define RUBY_VM_CORE_H

/* TruffleRuby minimal vm_core for building ripper parse */

#include "node.h"

/* included via method.h */
#include "internal.h"

const char *ruby_node_name(int node);

#endif