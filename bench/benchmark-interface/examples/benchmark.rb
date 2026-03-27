# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'benchmark'

require File.expand_path('clamp', File.dirname(__FILE__))

Benchmark.measure do
  clamp_a(10, 40, 90)
end

Benchmark.measure 'clamp_a1' do
  clamp_a(10, 40, 90)
end

Benchmark.realtime do
  clamp_a(10, 40, 90)
end

Benchmark.benchmark(Benchmark::CAPTION, 7, Benchmark::FORMAT, ">total:", ">avg:") do |x|
  tf = x.report('clamp_a1') { clamp_a(10, 40, 90) }
  tt = x.report('clamp_b1') { clamp_b(10, 40, 90) }
end

Benchmark.bm do |x|
  x.report { clamp_a(10, 40, 90) }
  x.report('clamp_a3') { clamp_a(10, 40, 90) }
  x.report('clamp_b3') { clamp_b(10, 40, 90) }
end

Benchmark.bmbm do |x|
  x.report { clamp_a(10, 40, 90) }
  x.report('clamp_a4') { clamp_a(10, 40, 90) }
  x.report('clamp_b4') { clamp_b(10, 40, 90) }
end
