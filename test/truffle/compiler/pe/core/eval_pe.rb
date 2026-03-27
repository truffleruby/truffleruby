# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "eval('14')", 14

example "eval('14 + 2')", 16

example "eval('[1, 2, 3]')[1]", 2

example "eval([1, 2, 3].inspect)[1]", 2

tagged counter example "eval(rand.to_s)"

example "eval('14', binding)", 14

example "b = binding; eval('temp = 14', b); eval('temp', b)", 14
