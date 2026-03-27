# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

def foo
  Truffle::Debug.throw_java_exception 'custom message'
end

at_exit do
  foo
end
