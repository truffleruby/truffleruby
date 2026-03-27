# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "true.frozen?", true
example "nil.frozen?", true

# int
example "3.frozen?", true

# long
example "(2**62).frozen?", true

# Bignum
example "(10 ** 100).frozen?", true

# double
example "3.5.frozen?", true

# Symbols are always frozen
example ":symbol.frozen?", true

# Object
example "Object.new.frozen?", false
example "Object.new.freeze.frozen?", true
