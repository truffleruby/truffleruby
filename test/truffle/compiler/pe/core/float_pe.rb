# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "14.2", 14.2

example "14.0 + 2", 16.0
counter example "14.0 + 0xfffffffffffffffffffffffffffffff"
example "14.0 + 2.0", 16.0
counter example "14.0 + rand"

example "14.0 * 2", 28.0
counter example "14.0 * 0xfffffffffffffffffffffffffffffff"
example "14.0 * 2.0", 28.0
counter example "14.0 * rand"

example "14.0 / 2", 7.0
counter example "14.0 / 0xfffffffffffffffffffffffffffffff"
example "14.0 / 2.0", 7.0
counter example "14.0 / rand"
