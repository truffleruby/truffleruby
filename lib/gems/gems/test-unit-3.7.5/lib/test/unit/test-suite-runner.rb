#--
#
# Author:: Nathaniel Talbott.
# Copyright:: Copyright (c) 2000-2003 Nathaniel Talbott. All rights reserved.
# Copyright:: Copyright (c) 2008-2011 Kouhei Sutou. All rights reserved.
# Copyright:: Copyright (c) 2024-2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

require "etc"

require_relative "test-run-context"

module Test
  module Unit
    class TestSuiteRunner
      @n_workers = Etc.respond_to?(:nprocessors) ? Etc.nprocessors : 1
      class << self
        def run_all_tests(result, options)
          yield(TestRunContext.new(self))
        end

        def n_workers
          @n_workers
        end

        def n_workers=(n)
          @n_workers = n
        end
      end

      def initialize(test_suite)
        @test_suite = test_suite
      end

      def run(worker_context, &progress_block)
        yield(TestSuite::STARTED, @test_suite.name)
        yield(TestSuite::STARTED_OBJECT, @test_suite)
        run_startup(worker_context)
        run_tests(worker_context, &progress_block)
      ensure
        begin
          run_shutdown(worker_context)
        ensure
          yield(TestSuite::FINISHED, @test_suite.name)
          yield(TestSuite::FINISHED_OBJECT, @test_suite)
        end
      end

      private
      def run_startup(worker_context)
        test_case = @test_suite.test_case
        return if test_case.nil? or !test_case.respond_to?(:startup)
        test_case.worker_id = worker_context.id
        begin
          test_case.startup
        rescue Exception
          raise unless handle_exception($!, worker_context.result)
        end
      end

      def run_tests(worker_context, &progress_block)
        @test_suite.tests.each do |test|
          run_test(test, worker_context, &progress_block)
        end
      end

      def run_test(test, worker_context)
        finished_is_yielded = false
        finished_object_is_yielded = false
        previous_event_name = nil
        event_listener = lambda do |event_name, *args|
          case previous_event_name
          when Test::Unit::TestCase::STARTED
            if event_name != Test::Unit::TestCase::STARTED_OBJECT
              yield(Test::Unit::TestCase::STARTED_OBJECT, test)
            end
          when Test::Unit::TestCase::FINISHED
            if event_name != Test::Unit::TestCase::FINISHED_OBJECT
              yield(Test::Unit::TestCase::FINISHED_OBJECT, test)
            end
            finished_object_is_yielded = true
          end

          case event_name
          when Test::Unit::TestCase::STARTED
            finished_is_yielded = false
            finished_object_is_yielded = false
          when Test::Unit::TestCase::FINISHED
            finished_is_yielded = true
          end

          previous_event_name = event_name
          yield(event_name, *args)
        end

        if test.method(:run).parameters[0] == [:req, :worker_context]
          test.run(worker_context, &event_listener)
        else
          # For backward compatibility. There are scripts that overrides
          # Test::Unit::TestCase#run without keyword arguments.
          test.run(worker_context.result, &event_listener)
        end

        if finished_is_yielded and not finished_object_is_yielded
          yield(Test::Unit::TestCase::FINISHED_OBJECT, test)
        end
      end

      def run_shutdown(worker_context)
        test_case = @test_suite.test_case
        return if test_case.nil? or !test_case.respond_to?(:shutdown)
        test_case.worker_id = worker_context.id
        begin
          test_case.shutdown
        rescue Exception
          raise unless handle_exception($!, worker_context.result)
        end
      end

      def handle_exception(exception, result)
        case exception
        when *ErrorHandler::PASS_THROUGH_EXCEPTIONS
          false
        else
          result.add_error(Error.new(@test_suite.test_case.name, exception))
          true
        end
      end
    end
  end
end

