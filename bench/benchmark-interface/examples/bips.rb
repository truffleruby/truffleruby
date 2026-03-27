# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'benchmark/ips'

require File.expand_path('clamp', File.dirname(__FILE__))

Benchmark.ips do |x|
  x.config(:time => 5, :warmup => 2)
  
  x.time = 5
  x.warmup = 2
  
  x.report { clamp_a(10, 40, 90) }
  x.report('clamp_a1') { clamp_a(10, 40, 90) }
  x.report('clamp_b1') { clamp_b(10, 40, 90) }

  x.report('clamp_a2') do |times|
    i = 0
    while i < times
      clamp_a(10, 40, 90)
      i += 1
    end
  end

  x.report('clamp_a3', 'clamp_a(10, 40, 90)')

  x.compare!
end
