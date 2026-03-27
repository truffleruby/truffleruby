# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative 'backtraces'

def meth_with_inlined_plus(n)
  noop = 42
  n + 2
end

check('inlined_method.backtrace') do
  meth_with_inlined_plus(nil)
end
