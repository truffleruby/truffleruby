#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2024 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

module Test
  module Unit
    class SubTestResult
      attr_accessor :stop_tag

      def initialize(parent_test_result)
        @parent_test_result = parent_test_result
        @stop_tag = nil
      end

      def add_run(result=self)
        @parent_test_result.add_run(result)
      end

      def add_pass
        @parent_test_result.add_pass
      end

      # Records an individual assertion.
      def add_assertion
        @parent_test_result.add_assertion
      end

      def add_error(error)
        @parent_test_result.add_error(error)
      end

      def add_failure(failure)
        @parent_test_result.add_failure(failure)
      end

      def add_pending(pending)
        @parent_test_result.add_pending(pending)
      end

      def add_omission(omission)
        @parent_test_result.add_omission(omission)
      end

      def add_notification(notification)
        @parent_test_result.add_notification(notification)
      end

      def passed?
        @parent_test_result.passed?
      end

      def stop
        throw @stop_tag
      end
    end
  end
end
