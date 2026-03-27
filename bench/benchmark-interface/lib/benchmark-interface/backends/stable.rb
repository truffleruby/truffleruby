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
    module Stable
      extend BenchmarkInterface::Timing

      def self.run(benchmark_set, names, options)
        full_time = options['--time']
        freq = options['--freq']
        elapsed = options['--elapsed']
        print_iterations = options['--iterations']
        
        inner_iterations = benchmark_set.iterations
        
        benchmark_set.benchmarks(names).each do |benchmark|
          puts benchmark.name
          block = benchmark.block

          start_time = get_time

          while get_time - start_time < full_time
            start_round_time = get_time
            block.call
            round_time = get_time - start_round_time
            
            ips = 1 / round_time
            puts 1 if print_iterations
            puts get_time - start_time if elapsed
            puts ips * inner_iterations
          end
        end
      end

    end
  end
end
