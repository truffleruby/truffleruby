# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

side = 512 * 1024
big_string = ("a".b * side + "é".b + "z".b * side)[1...-1]
result = big_string.byteslice(4, 8)
# Truffle::Debug.tstring_to_debug_string(big_string)

benchmark "core-string-many-substrings-of-large-substring" do
  i = 0
  while i < 1000
    result = big_string.byteslice(i, 8)
    i += 8
  end
end
