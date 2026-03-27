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
    module Bench9000

      MIN_SAMPLING_TIME = 0.1 # seconds

      def self.run(benchmark_set, names, options)
        unless names.size == 1
          abort 'The bench9000 backend only works when you run just one benchmark at a time - specify the name on the command line'
        end

        unless options['--no-scale']
          min_time = benchmark_set.benchmarks.map(&:basic_iteration_time).min

          if min_time < MIN_SAMPLING_TIME
            short_iterations = true
            samples = (MIN_SAMPLING_TIME / min_time / MIN_SAMPLING_TIME).to_i
            puts "These are short benchmarks - we're running each #{samples} times so they take about a second and using the micro harness"
          end
        end

        benchmark = benchmark_set.benchmark(names.first)
        block = benchmark.block

        Object.instance_eval do
          if short_iterations
            define_method(:micro_harness_input) do
              nil
            end

            define_method(:micro_harness_iterations) do
              samples
            end

            define_method(:micro_harness_sample) do |input|
              block.call
            end

            define_method(:micro_harness_expected) do
              raise 'not expecting this to be called, as we\'ve patched harness_verify'
            end
          else
            define_method(:harness_input) do
              nil
            end

            define_method(:harness_sample) do |input|
              block.call
            end

            define_method(:harness_verify) do |output|
              true
            end
          end

        end
        
        @loading_real = true

        if short_iterations
          benchmark_interface_original_require 'bench9000/micro-harness'

          Object.instance_eval do
            define_method(:harness_verify) do |output|
              true
            end
          end
        end

        benchmark_interface_original_require 'bench9000/harness'
      end
      
      def self.loading_real?
        @loading_real
      end

    end
  end
end
