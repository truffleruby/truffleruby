# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "[3, 1, 2][1]", 1

example "[3, 1, 2].sort[1]", 2

# [GR-39718] Relies on PEA of the byte[], but it is allocated by AMD64CalcStringAttributesMacro currently
tagged example "[14].pack('C').getbyte(0)", 14

example "sum = 0; [1,2,3].each { |x| sum += x }; sum", 6

example "sum = 0; [7].each { |x| sum += x }; sum", 7
