# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  module Backends
    module Bips

      LONG_ITERATION_THRESHOLD = 0.1 # seconds
      
      def self.run(benchmark_set, names, options)
        BenchmarkInterface.require_rubygems
        benchmark_interface_original_require 'benchmark/ips'

        unless options['--no-scale']
          if benchmark_set.benchmarks.map(&:basic_iteration_time).max > LONG_ITERATION_THRESHOLD
            long_iterations = true
            puts "These are long benchmarks - we're increasing warmup and sample time"
          end
        end

        ::Benchmark.ips do |x|
          x.iterations = 3

          if long_iterations
            x.time = 10
            x.warmup = 10
          end

          benchmark_set.benchmarks(names).each do |benchmark|
            x.report benchmark.name, &benchmark.block
          end

          x.compare!
        end
      end
      
    end
  end
end
