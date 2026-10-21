/**
 * @file prism.hpp
 *
 * The main header file for the C++ interface to the prism parser. It adds
 * ownership, checked casts between node types, and callable-based iteration on
 * top of the C API. It never wraps the C structs: every function in it takes
 * and returns the same pm_*_t types that prism.h declares, so the two can be
 * mixed freely. It requires C++17 or later.
 */
#ifndef PRISM_HPP
#define PRISM_HPP

#include "prism.h"

#include "prism/arena.hpp"
#include "prism/ast.hpp"
#include "prism/buffer.hpp"
#include "prism/node.hpp"
#include "prism/node_type.hpp"
#include "prism/options.hpp"
#include "prism/parser.hpp"
#include "prism/stringy.hpp"

#endif
