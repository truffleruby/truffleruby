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
  it "supports using arrow keys first" do
    # --readline is needed otherwise Readline is not used when stdin is not a TTY.
    IO.popen([*ruby_exe, "-S", "irb", "-f", "--prompt=simple", "--readline"], "r+") do |io|
      io.gets.should == "Switch to inspect mode.\n"

      if RUBY_ENGINE == "truffleruby"
        io.puts "B = 42"
        io.gets.should == ">> B = 42\n"
        io.gets.should == "=> 42\n"
      end

      io.puts "\e[B" # down arrow
      # JLine adds a bell character (since there is no history)
      echo = io.gets
      [">> \n", ">> [B\n"].should include(echo)

      if echo == ">> [B\n" # "[B" (echo'd by JLine) looks like a legal expression to IRB so we need to complete it
        io.puts "]"
        [">>   ]\n", "?>   ]\n"].should include(io.gets)
        io.gets.should == "=> [42]\n"
      end

      io.puts "22 + 33"
      # The extra bell character causes a continuation line
      [">> 22 + 33\n", "?> 22 + 33\n"].should include(io.gets)
      io.gets.should == "=> 55\n"

      io.puts "exit"
      io.gets.should == ">> exit\n"
    end
  end
end
