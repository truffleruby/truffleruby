# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# TODO CS 19-Feb-16 Not compliant with MRI - here as a regression test

require_relative 'tracing'

def add(a)
  a + yield
end

outside_trace = 1
captured_in_scope = 2
modified = 3

set_trace_func $trace_proc

inside_trace = 4
modified = 5

result = add(14) {
  captured_in_scope
}

result

set_trace_func nil

check('binding.trace')
