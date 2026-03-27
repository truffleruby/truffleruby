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
    module Simple
      extend BenchmarkInterface::Timing

      INITIAL_ITERATIONS = 1
      MAX_ITERATIONS = 2147483647

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
          iterations = INITIAL_ITERATIONS

          while get_time - start_time < full_time
            start_round_time = get_time
            result = BenchmarkInterface.run_n_iterations(iterations, &block)
            round_time = get_time - start_round_time
            benchmark.verify!(result)
            
            # If the round time was very low and so very imprecise then we may
            # get a wild number of iterations next time.
            if round_time < 0.01
              # Double to scale up the iterations to a value we can measure. Do
              # so without printing the ips (as otherwise you'll see a storm of
              # them as it scales up).
              iterations *= 2
            else
              # If the iteration time is at least a hundredth of a second, we
              # can print an ips and adjust for the next round to try to make
              # it take a second.
              ips = iterations / round_time
              puts iterations if print_iterations
              puts get_time - start_time if elapsed
              puts ips * inner_iterations
              iterations = (ips * freq).to_i
            end

            # If we're running 2^31 iterations and it's still not enough
            # then we need to give up.
            if iterations > MAX_ITERATIONS
              puts 'optimised away'
              break
            end
            
            # If we rounded to zero, run at least one iteration.
            iterations = 1 if iterations.zero?
          end
        end
      end

    end
  end
end
