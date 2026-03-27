# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Beware, RubyDebugTest use hard-coded line numbers from this file!

def temperature_in_city(name)
  cityArray = name.bytes
  citySum = cityArray.reduce(0, :+)
  weatherTemperature = citySum % 36
  blt = true
  blf = false
  null = nil
  nm1 = 1
  nm11 = 1.111
  nme = 35e45
  nc = 2 + 3i
  nr = Rational(0.3)
  str = 'A String'
  symbol = :symbolic
  arr = [1, '2', 3.56, blt, nil, str]
  hash = {:a => 1, 'b' => 2}
  struct = Struct.new(:a, :b).new(1, 2)
  res = nme + nm1
  res
end

temperature_in_city('Panama')

def types_main
  temperature_in_city('Panama')
end

Polyglot.export_method :types_main
