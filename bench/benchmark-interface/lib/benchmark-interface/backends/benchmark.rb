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
    module Bm

      MIN_SAMPLING_TIME = 0.1 # seconds

      def self.run(benchmark_set, names, options)
        # If we don't remove these we'll get a warning when we load the real
        # implementation.
          
        ::Benchmark.send(:remove_const, :CAPTION) if defined?(::Benchmark::CAPTION)
        ::Benchmark.send(:remove_const, :FORMAT)  if defined?(::Benchmark::FORMAT)

        benchmark_interface_original_require 'benchmark'

        unless options['--no-scale']
          min_time = benchmark_set.benchmarks.map(&:basic_iteration_time).min

          if min_time < MIN_SAMPLING_TIME
            short_iterations = true
            samples = (MIN_SAMPLING_TIME / min_time / MIN_SAMPLING_TIME).to_i
            puts "These are short benchmarks - we're running each #{samples} times so they take about a second"
          end
        end

        label_width = benchmark_set.benchmarks(names).map(&:name).map(&:size).max

        block = Proc.new do |x|
          benchmark_set.benchmarks(names).each do |benchmark|
            block = benchmark.block
            if short_iterations
              x.report(benchmark.name) do
                BenchmarkInterface.run_n_iterations(samples, &block)
              end
            else
              x.report(benchmark.name, &benchmark.block)
            end
          end
        end

        if self == BmBm
          ::Benchmark.bmbm label_width, &block
        else
          ::Benchmark.bm label_width, &block
        end
      end

    end

    module BmBm

      def self.run(benchmark_set, names, options)
        Bm.run benchmark_set, names, options
      end

    end
  end
end
