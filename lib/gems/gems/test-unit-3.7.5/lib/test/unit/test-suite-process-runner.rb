#--
#
# Author:: Tsutomu Katsube.
# Copyright:: Copyright (c) 2025 Tsutomu Katsube. All rights reserved.
# License:: Ruby license.

require "socket"

require_relative "process-test-result"
require_relative "sub-test-result"
require_relative "test-process-run-context"
require_relative "test-suite-runner"

module Test
  module Unit
    class TestSuiteProcessRunner < TestSuiteRunner
      MAIN_TO_WORKER_INPUT_FILENO = 3
      WORKER_TO_MAIN_OUTPUT_FILENO = 4

      class << self
        class Worker
          attr_reader :main_to_worker_output, :worker_to_main_input
          def initialize(pid, main_to_worker_output, worker_to_main_input)
            @pid = pid
            @main_to_worker_output = main_to_worker_output
            @worker_to_main_input = worker_to_main_input
          end

          def receive
            Marshal.load(@worker_to_main_input)
          end

          def send(data)
            Marshal.dump(data, @main_to_worker_output)
            @main_to_worker_output.flush
          end

          def wait
            begin
              Process.waitpid(@pid)
            ensure
              begin
                @main_to_worker_output.close
              ensure
                @worker_to_main_input.close
              end
            end
          end
        end

        def run_all_tests(result, options)
          n_workers = TestSuiteRunner.n_workers
          test_suite = options[:test_suite]

          start_tcp_server do |tcp_server|
            workers = []
            begin
              n_workers.times do |i|
                load_paths = options[:load_paths]
                base_directory = options[:base_directory]
                test_paths = options[:test_paths]
                command_line = [Gem.ruby, File.join(__dir__, "process-worker.rb")]
                load_paths.each do |load_path|
                  command_line << "--load-path" << load_path
                end
                unless base_directory.nil?
                  command_line << "--base-directory" << base_directory
                end
                command_line << "--worker-id" << (i + 1).to_s
                if Gem.win_platform?
                  local_address = tcp_server.local_address
                  command_line << "--ip-address" << local_address.ip_address
                  command_line << "--ip-port" << local_address.ip_port.to_s
                end
                command_line.concat(test_paths)
                if Gem.win_platform?
                  # On Windows, file descriptors 3 and above cannot be passed to
                  # child processes.
                  pid = spawn(*command_line)
                  data_socket = tcp_server.accept
                  workers << Worker.new(pid, data_socket, data_socket)
                else
                  main_to_worker_input, main_to_worker_output = IO.pipe
                  worker_to_main_input, worker_to_main_output = IO.pipe
                  pid = spawn(*command_line, {MAIN_TO_WORKER_INPUT_FILENO => main_to_worker_input,
                                              WORKER_TO_MAIN_OUTPUT_FILENO => worker_to_main_output})
                  main_to_worker_input.close
                  worker_to_main_output.close
                  workers << Worker.new(pid, main_to_worker_output, worker_to_main_input)
                end
              end

              run_context = TestProcessRunContext.new(self)
              yield(run_context)
              run_context.progress_block.call(TestSuite::STARTED, test_suite.name)
              run_context.progress_block.call(TestSuite::STARTED_OBJECT, test_suite)

              worker_inputs = workers.collect(&:worker_to_main_input)
              until run_context.test_names.empty? do
                select_each_worker(worker_inputs, workers) do |_, worker, data|
                  case data[:status]
                  when :ready
                    test_name = run_context.test_names.shift
                    break if test_name.nil?
                    worker.send(test_name)
                  when :result
                    add_result(result, data)
                  when :event
                    emit_event(options[:event_listener], data)
                  end
                end
              end
              workers.each do |worker|
                worker.send(nil)
              end
              until worker_inputs.empty? do
                select_each_worker(worker_inputs, workers) do |worker_to_main_input, worker, data|
                  case data[:status]
                  when :result
                    add_result(result, data)
                  when :event
                    emit_event(options[:event_listener], data)
                  when :done
                    worker_inputs.delete(worker_to_main_input)
                    worker.send(nil)
                  end
                end
              end
            ensure
              workers.each do |worker|
                worker.wait
              end
            end

            run_context.progress_block.call(TestSuite::FINISHED, test_suite.name)
            run_context.progress_block.call(TestSuite::FINISHED_OBJECT, test_suite)
          end
        end

        private
        def start_tcp_server
          if Gem.win_platform?
            TCPServer.open("127.0.0.1", 0) do |tcp_server|
              yield(tcp_server)
            end
          else
            yield
          end
        end

        def select_each_worker(worker_inputs, workers)
          readables, = IO.select(worker_inputs)
          readables.each do |worker_to_main_input|
            worker = workers.find do |w|
              w.worker_to_main_input == worker_to_main_input
            end
            data = worker.receive
            yield(worker_to_main_input, worker, data)
          end
        end

        def add_result(result, data)
          action = data[:action]
          args = data[:args]
          result.__send__(action, *args)
        end

        def emit_event(event_listener, data)
          event_name = data[:event_name]
          args = data[:args]
          event_listener.call(event_name, *args)
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
          run_context.test_names << test_suite.name
        else
          test_suite.tests.each do |test|
            if test.is_a?(TestSuite)
              run_tests_recursive(test, worker_context, &progress_block)
            else
              run_context.test_names << test.name
            end
          end
        end
      end
    end
  end
end
