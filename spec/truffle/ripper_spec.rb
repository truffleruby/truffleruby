# Copyright (c) 2026 TruffleRuby contributors.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Ripper" do
  it "is implemented with Ruby::Prism and does not load the prism gem" do
    ruby_exe(<<~RUBY).should == "true\n[:program, [[:@int, \"1\", [1, 0]]]]\nnil\n"
      require "ripper"
      p Ripper.equal?(Ruby::Prism::Translation::Ripper)
      p Ripper.sexp("1")
      p defined?(::Prism)
    RUBY
  end
end
