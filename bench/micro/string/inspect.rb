# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

result = ""
simple = "abcdefghij\nklmnopqrst\nuvwxyz"
utf8 = "abçdéfghîĵ\nklmñöpqrst\nuvwxyz"

benchmark "core-string-inspect-simple" do
  result = simple.inspect
end

benchmark "core-string-inspect-utf8" do
  result = utf8.inspect
end
