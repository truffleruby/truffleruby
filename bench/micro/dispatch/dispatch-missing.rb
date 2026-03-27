# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Callee
  def method_missing(m)
    :foo
  end
end

callees = Array.new(1000) { Callee.new }

benchmark 'dispatch-missing' do
  i = 0
  while i < 1000
    callees[i].call
    i += 1
  end
end
