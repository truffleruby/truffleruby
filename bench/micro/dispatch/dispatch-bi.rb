# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class CalleeEven
  def call; :even; end
end

class CalleeOdd
  def call; :odd; end
end

callees = Array.new(1000) { |i| i.even? ? CalleeEven.new : CalleeOdd.new }

benchmark 'dispatch-bi' do
  i = 0
  while i < 1000
    callees[i].call
    i += 1
  end
end