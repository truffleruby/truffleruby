# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  module Backends
    module FixedIterations

      def self.get_time
        Process.clock_gettime(Process::CLOCK_MONOTONIC)
      end

      def self.run(benchmark_set, names, options)
        print_iterations = options['--iterations']
        elapsed = options['--elapsed']
        print_ips = options['--ips']
        fixed_iterations = options['--fixed-iterations'].sort.freeze
        option_start_time = options['--start-time']

        benchmarks = benchmark_set.benchmarks(names)
        raise "Can only run a single benchmark with --start-time" if option_start_time and benchmarks.size > 1
        benchmarks.each do |benchmark|
          puts benchmark.name
          block = benchmark.block

          iterations = fixed_iterations.dup
          total_iterations = fixed_iterations.last

          next_iter = iterations.shift
          start_time = option_start_time || get_time
          (1..total_iterations).each do |iter|
            block.call

            if iter == next_iter
              since_start = get_time - start_time
              puts iter if print_iterations
              puts since_start if elapsed
              puts iter / since_start if print_ips

              next_iter = iterations.shift
            end
          end
        end
      end

    end
  end
end
