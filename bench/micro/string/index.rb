# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

small = 10
large = 1_000_000

def str(n)
  Array.new(n, 'a'.ord).pack('C*')
end

s_small = str(small)
s_large = str(large)

result = s_small.index("b")
raise if result
result = s_large.index("b")
raise if result

benchmark "core-string-index-#{small}" do
  result = s_small.index("b")
end

benchmark "core-string-index-#{large}" do
  result = s_large.index("b")
end
