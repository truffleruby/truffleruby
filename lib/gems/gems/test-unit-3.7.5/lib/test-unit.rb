# Copyright (C) 2012-2015  Kouhei Sutou <kou@clear-code.com>

require_relative "test/unit/warning"

module Test
  module Unit
    autoload :TestCase, "#{__dir__}/test/unit/testcase"
    autoload :AutoRunner, "#{__dir__}/test/unit/autorunner"
  end
end

unless respond_to?(:run_test, true)
  # experimental. It is for "ruby -rtest-unit -e run_test test/test_*.rb".
  # Is this API OK or dirty?
  def run_test
    self.class.send(:undef_method, :run_test)
    require_relative "test/unit"
  end
end
