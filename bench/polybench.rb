# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# A small subset of PolyBenchLauncher (in the /vm suite)
# to make it easier to run on an already-built GraalVM or other Ruby.
# Use it like:
# jt -u CONFIG ruby --experimental-options --engine.Compilation=false bench/polybench.rb ../graal/vm/benchmarks/interpreter/richards.rb

require 'benchmark'

benchmark = File.expand_path(ARGV.fetch(0))
BASENAME = File.basename(benchmark)

require benchmark

WARMUP_ITERATIONS = 20
MEASURE_ITERATIONS = 30

def repeat_iterations(iterations)
  iterations.times { |i|
    t = Benchmark.realtime { run() }
    puts "[#{BASENAME}] iteration #{i}: #{'%.2f' % (t * 1000)} ms"
  }
end

puts '::: Running warmup :::'
repeat_iterations(WARMUP_ITERATIONS)

puts
puts '::: Running :::'
repeat_iterations(MEASURE_ITERATIONS)
