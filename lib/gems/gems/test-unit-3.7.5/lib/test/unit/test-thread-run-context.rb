#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

require_relative "test-run-context"

module Test
  module Unit
    class TestThreadRunContext < TestRunContext
      attr_reader :queue
      attr_accessor :progress_block
      attr_accessor :parallel_unsafe_tests
      def initialize(runner_class, queue)
        super(runner_class)
        @queue = queue
        @progress_block = nil
        @parallel_unsafe_tests = []
      end
    end
  end
end
