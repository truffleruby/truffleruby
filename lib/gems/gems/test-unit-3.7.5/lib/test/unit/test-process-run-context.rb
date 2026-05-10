#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

require_relative "test-run-context"

module Test
  module Unit
    class TestProcessRunContext < TestRunContext
      attr_reader :test_names
      attr_accessor :progress_block
      def initialize(runner_class)
        super(runner_class)
        @test_names = []
        @progress_block = nil
      end
    end
  end
end
