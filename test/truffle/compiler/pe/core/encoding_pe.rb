# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "Encoding.compatible?('abc', 'def')", Encoding::UTF_8
example "Encoding.compatible?(Encoding::UTF_8, Encoding::US_ASCII)", Encoding::UTF_8
example "Encoding.compatible?(Encoding::UTF_8, Encoding::ASCII_8BIT)", nil
example "Encoding.compatible?('abc', Encoding::US_ASCII)", Encoding::UTF_8
