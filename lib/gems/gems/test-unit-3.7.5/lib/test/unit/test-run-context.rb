#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

module Test
  module Unit
    class TestRunContext
      attr_reader :runner_class
      def initialize(runner_class)
        @runner_class = runner_class
      end
    end
  end
end
