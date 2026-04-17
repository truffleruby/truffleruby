#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

module Test
  module Unit
    class ProcessTestResult
      def initialize(output)
        @output = output
      end

      def add_run
        send_result(__method__)
      end

      def add_pass
        send_result(__method__)
      end

      # Records an individual assertion.
      def add_assertion
        send_result(__method__)
      end

      def add_error(error)
        send_result(__method__, error)
      end

      def add_failure(failure)
        send_result(__method__, failure)
      end

      def add_pending(pending)
        send_result(__method__, pending)
      end

      def add_omission(omission)
        send_result(__method__, omission)
      end

      def add_notification(notification)
        send_result(__method__, notification)
      end

      private

      def send_result(action, *args)
        Marshal.dump({status: :result, action: action, args: args}, @output)
        @output.flush
      end
    end
  end
end
