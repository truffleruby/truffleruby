#--
#
# Author:: Nathaniel Talbott.
# Copyright:: Copyright (c) 2000-2003 Nathaniel Talbott. All rights reserved.
# Copyright:: Copyright (c) 2008-2011 Kouhei Sutou. All rights reserved.
# License:: Ruby license.

require 'test/unit/error'
require_relative 'test-suite-runner'

module Test
  module Unit

    # A collection of tests which can be #run.
    #
    # Note: It is easy to confuse a TestSuite instance with
    # something that has a static suite method; I know because _I_
    # have trouble keeping them straight. Think of something that
    # has a suite method as simply providing a way to get a
    # meaningful TestSuite instance.
    class TestSuite
      attr_reader :name, :tests, :test_case, :start_time, :elapsed_time

      # Test suite that has higher priority is ran prior to
      # test suites that have lower priority.
      attr_accessor :priority

      STARTED = name + "::STARTED"
      STARTED_OBJECT = name + "::STARTED::OBJECT"
      FINISHED = name + "::FINISHED"
      FINISHED_OBJECT = name + "::FINISHED::OBJECT"

      # Creates a new TestSuite with the given name.
      def initialize(name="Unnamed TestSuite", test_case=nil)
        @name = name
        @tests = []
        @test_case = test_case
        @priority = 0
        @start_time = nil
        @elapsed_time = nil
      end

      def parallel_safe?
        return true if @test_case.nil?
        @test_case.parallel_safe?
      end

      # Runs the tests and/or suites contained in this
      # TestSuite.
      def run(result, runner_class: nil, &progress_block)
        runner_class ||= TestSuiteRunner
        runner_class.new(self).run(result) do |event, *args|
          case event
          when STARTED
            @start_time = Time.now
          when FINISHED
            @elapsed_time = Time.now - @start_time
          end
          yield(event, *args)
        end
      end

      # Adds the test to the suite.
      def <<(test)
        @tests << test
        self
      end

      def delete(test)
        @tests.delete(test)
      end

      def delete_tests(tests)
        @tests -= tests
      end

      # Returns the rolled up number of tests in this suite;
      # i.e. if the suite contains other suites, it counts the
      # tests within those suites, not the suites themselves.
      def size
        total_size = 0
        @tests.each { |test| total_size += test.size }
        total_size
      end

      def empty?
        size.zero?
      end

      # Overridden to return the name given the suite at
      # creation.
      def to_s
        @name
      end

      # It's handy to be able to compare TestSuite instances.
      def ==(other)
        return false unless(other.kind_of?(self.class))
        return false unless(@name == other.name)
        @tests == other.tests
      end

      def passed?
        @tests.all?(&:passed?)
      end
    end
  end
end
