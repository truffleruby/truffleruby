# Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'

describe "Tools" do
  describe "--engine.TraceCompilation" do
    guard -> { TruffleRuby.jit? } do
      it "works and outputs to STDERR" do
        options = [
            "--engine.TraceCompilation",
            "--experimental-options",
            "--engine.BackgroundCompilation=false",
        ].join(" ")
        err = tmp("err.txt")
        begin
          ruby_exe("2000.times {}", options: options, args: "2>#{err}")
          File.read(err).should include "[engine] opt done"
        ensure
          rm_r err
        end
      end
    end
  end

  describe "SIGQUIT" do
    it "shows Java stacktraces" do
      code = 'Process.kill :SIGQUIT, Process.pid; sleep 1'
      out = ruby_exe(code, args: "2>&1")
      out.should.include?("Full thread dump")
      out.should.include?("RubyLauncher.runRubyMain")
    end
  end

  describe "SIGALRM" do
    it "shows Ruby backtraces" do
      code = 'Process.kill :SIGALRM, Process.pid; sleep 1'
      out = ruby_exe(code, args: "2>&1")
      out.should.include?("All Thread and Fiber backtraces:")
      out.should.include?(":in 'Truffle::Debug.show_backtraces'")
    end
  end

  describe "--coverage" do
    before :each do
      @file = tmp("main.rb")
      touch(@file) { |f| f.write "puts 6 * 7" }
    end

    after :each do
      rm_r @file
    end

    it "is available and works" do
      out = ruby_exe(@file, options: "--coverage")
      out.should =~ /#{Regexp.escape @file}.+100(\.\d*)?%/
      out.should.include?("42")
    end

    it "works for internal sources" do
      out = ruby_exe(@file, options: "--coverage --experimental-options --coverage.TrackInternal")
      out.should =~ /#{Regexp.escape @file}.+100(\.\d*)?%/
      out.should.include?("42")
    end
  end

  describe "--cpusampler" do
    it "works if Thread#kill is used" do
      code = <<~RUBY
      def foo
        n = 0
        loop { yield_self { n += 1 } }
        n
      end
      t = Thread.new { foo }
      sleep 1
      p :kill
      t.kill
      t.join
      RUBY
      out = ruby_exe(code, options: "--cpusampler")
      out.should.include?(":kill")
      out.should.include?("Sampling Histogram")
      out.should.include?("Kernel#loop")
      out.should_not.include?('KillException')
    end

    it "works if interrupted by Ctrl+C" do
      code = <<~RUBY
      def foo
        n = 0
        loop { yield_self { n += 1 } }
        n
      end
      Thread.new { foo }
      sleep 0.1
      Process.kill :INT, Process.pid
      sleep
      RUBY
      out = ruby_exe(code, options: "--cpusampler", args: "2>&1", exit_status: :SIGINT)
      out.should.include?('Interrupt (Interrupt)')
      out.should.include?("Sampling Histogram")
      out.should.include?("Kernel#loop")
    end
  end
end
