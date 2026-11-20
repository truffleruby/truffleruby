# Copyright (c) 2026 TruffleRuby contributors.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Ruby::Prism" do
  it "is autoloaded and does not load the prism gem" do
    ruby_exe(<<~RUBY).should == "ruby/prism\nRuby::Prism::ProgramNode\nnil\ntrue\n"
      puts Ruby.autoload?(:Prism)
      puts Ruby::Prism.parse("1 + 2").value.class
      p defined?(::Prism)
      p $LOADED_FEATURES.include?("\#{RbConfig::CONFIG['rubylibdir']}/ruby/prism.rb")
    RUBY
  end

  it "is independent of the Prism module of the prism gem" do
    ruby_exe(<<~RUBY).should == "false\nPrism::ProgramNode\nRuby::Prism::ProgramNode\n"
      require "prism"
      p Prism.equal?(Ruby::Prism)
      puts Prism.parse("1 + 2").value.class
      puts Ruby::Prism.parse("1 + 2").value.class
    RUBY
  end

  it "is the namespace of the nodes returned by #syntax_tree" do
    ruby_exe(<<~RUBY).should == "Ruby::Prism::BlockNode\nnil\n"
      puts proc { 42 }.syntax_tree.class
      p defined?(::Prism)
    RUBY
  end

  it "implements Ripper without loading the prism gem" do
    ruby_exe(<<~RUBY).should == "true\n[:program, [[:@int, \"1\", [1, 0]]]]\nnil\n"
      require "ripper"
      p Ripper.equal?(Ruby::Prism::Translation::Ripper)
      p Ripper.sexp("1")
      p defined?(::Prism)
    RUBY
  end
end
