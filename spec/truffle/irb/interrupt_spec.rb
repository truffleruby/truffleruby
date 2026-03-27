# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "IRB" do
  it "can be interrupted with Ctrl+C" do
    # --readline is needed otherwise Readline is not used when stdin is not a TTY.
    IO.popen([*ruby_exe, "-S", "irb", "-f", "--prompt=simple", "--readline"], "r+") do |io|
      io.gets.should == "Switch to inspect mode.\n"

      io.puts "22 + 33"
      io.gets.should == ">> 22 + 33\n"
      io.gets.should == "=> 55\n"

      sleep 0.1 # Make sure irb is waiting for input
      Process.kill(:INT, io.pid) # Ctrl+C
      io.gets.should == ">> ^C\n"

      io.puts "exit"
      [">> exit\n", "\n"].should include(io.gets)
    end
  end
end
