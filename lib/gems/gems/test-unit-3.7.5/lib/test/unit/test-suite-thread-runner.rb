#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2024-2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

require_relative "sub-test-result"
require_relative "test-suite-runner"
require_relative "test-thread-run-context"

module Test
  module Unit
    class TestSuiteThreadRunner < TestSuiteRunner
      class << self
        def run_all_tests(result, options)
          n_workers = TestSuiteRunner.n_workers
          test_suite = options[:test_suite]

          queue = Thread::Queue.new
          run_context = TestThreadRunContext.new(self, queue)
          yield(run_context)
          run_context.progress_block.call(TestSuite::STARTED, test_suite.name)
          run_context.progress_block.call(TestSuite::STARTED_OBJECT, test_suite)
          run_context.parallel_unsafe_tests.each(&:call)
          n_workers.times do
            queue << nil
          end

          workers = []
          sub_exceptions = []
          n_workers.times do |i|
            workers << Thread.new(i + 1) do |worker_id|
              begin
                loop do
                  task = queue.pop
                  break if task.nil?
                  catch do |stop_tag|
                    task.call(stop_tag, worker_id)
                  end
                end
              rescue Exception => exception
                sub_exceptions << exception
              end
            end
          end
          workers.each(&:join)

          run_context.progress_block.call(TestSuite::FINISHED, test_suite.name)
          run_context.progress_block.call(TestSuite::FINISHED_OBJECT, test_suite)

          sub_exceptions.each do |exception|
            raise exception
          end
        end
      end

      def run(worker_context, &progress_block)
        worker_context.run_context.progress_block = progress_block
        run_tests_recursive(@test_suite, worker_context, &progress_block)
      end

      private
      def run_tests_recursive(test_suite, worker_context, &progress_block)
        run_context = worker_context.run_context
        if test_suite.have_fixture?
          task = lambda do |stop_tag, worker_id|
            sub_result = SubTestResult.new(worker_context.result)
            sub_result.stop_tag = stop_tag
            sub_runner = TestSuiteRunner.new(test_suite)
            sub_worker_context = WorkerContext.new(worker_id, run_context, sub_result)
            sub_runner.run(sub_worker_context, &progress_block)
          end
          run_context.queue << task
        else
          test_suite.tests.each do |test|
            if test.is_a?(TestSuite)
              run_tests_recursive(test, worker_context, &progress_block)
            elsif test_suite.parallel_safe?
              task = lambda do |stop_tag, worker_id|
                sub_result = SubTestResult.new(worker_context.result)
                sub_result.stop_tag = stop_tag
                sub_worker_context = WorkerContext.new(worker_id, run_context, sub_result)
                run_test(test, sub_worker_context, &progress_block)
              end
              run_context.queue << task
            else
              run_context.parallel_unsafe_tests << lambda do
                run_test(test, worker_context, &progress_block)
              end
            end
          end
        end
      end
    end
  end
end
