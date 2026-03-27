# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Options" do

  it "have a default" do
    ruby_exe("p Truffle::Boot.get_option('dispatch-cache')").should == "8\n"
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')").should == "false\n"
  end

  it "can be set explicitly back to their default value" do
    ruby_exe("p Truffle::Boot.get_option('dispatch-cache')", options: "--experimental-options --dispatch-cache=8").should == "8\n"
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --polyglot-stdio=false").should == "false\n"
  end

  it "can be set explicitly to a value" do
    ruby_exe("p Truffle::Boot.get_option('dispatch-cache')", options: "--experimental-options --dispatch-cache=99").should == "99\n"
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --polyglot-stdio=true").should == "true\n"
  end

  it "can be set explicitly to an implicit value" do
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --polyglot-stdio").should == "true\n"
  end

  it "can be set using a simple referenced option" do
    ruby_exe("p Truffle::Boot.get_option('dispatch-cache')", options: "--experimental-options --default-cache=99").should == "99\n"
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --embedded").should == "true\n"
  end

  it "can be set using a negated referenced option" do
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --platform-native=false").should == "true\n"
  end

  it "take an explicit value over a modified referenced option" do
    ruby_exe("p Truffle::Boot.get_option('dispatch-cache')", options: "--experimental-options --default-cache=101 --dispatch-cache=99").should == "99\n"
    ruby_exe("p Truffle::Boot.get_option('dispatch-cache')", options: "--experimental-options --dispatch-cache=99 --default-cache=101").should == "99\n"
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --embedded --polyglot-stdio=false").should == "false\n"
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --polyglot-stdio=false --embedded").should == "false\n"
  end

end
