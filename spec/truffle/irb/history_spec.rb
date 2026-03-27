# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "IRB" do
  it "ignores key sequences that would move past history in singleline mode" do
    # --readline is needed otherwise Reline is not used when stdin is not a TTY.
    IO.popen([*ruby_exe, "-S", "irb", "-f", "--prompt=simple", "--readline", "--singleline"], "r+") do |io|
      io.gets.should == "Switch to inspect mode.\n"

      io.puts "\C-n" # next-history (none)
      line = io.gets
      line.should.start_with?(">> ")
      line.should.end_with?("\n")

      # Prove that the session is still valid.
      io.puts "1+1"
      io.gets.should.include? "1+1"
      io.gets.should.include? "=> 2\n"

      io.puts "exit"
      io.gets.should.include? "exit"
    end
  end
end
