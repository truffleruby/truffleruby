# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class CalleeUno
  def call; :uno; end
end

class CalleeDos
  def call; :dos; end
end

class CalleeTres
  def call; :tres; end
end

callees = Array.new(1000) do |i|
  case i % 3
  when 0; CalleeUno.new
  when 1; CalleeDos.new
  when 2; CalleeTres.new
  end
end

benchmark 'dispatch-tri' do
  i = 0
  while i < 1000
    callees[i].call
    i += 1
  end
end
