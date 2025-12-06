# frozen_string_literal: true

require "bundler/gem_tasks"
require "rake/testtask"

Rake::TestTask.new(:test) do |t|
  t.libs << "test"
  t.libs << "lib"
  t.test_files = FileList["test/repl_type_completor/test_*.rb"]
end

# To make sure they have been correctly setup for Ruby CI.
desc "Run each repl_type_completor test file in isolation."
task :test_in_isolation do
  failed = false

  FileList["test/repl_type_completor/test_*.rb"].each do |test_file|
    ENV["TEST"] = test_file
    begin
      Rake::Task["test"].execute
    rescue => e
      failed = true
      msg = "Test '#{test_file}' failed when being executed in isolation. Please make sure 'rake test TEST=#{test_file}' passes."
      separation_line = '=' * msg.length

      puts <<~MSG
        #{separation_line}
        #{msg}
        #{separation_line}
      MSG
    end
  end

  fail "Some tests failed when being executed in isolation" if failed
end

task default: %i[test test_in_isolation]
