#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

module Test
  module Unit
    class WorkerContext
      attr_reader :id
      attr_reader :run_context
      attr_reader :result
      def initialize(id, run_context, result)
        @id = id
        @run_context = run_context
        @result = result
      end
    end
  end
end
