# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --platform-native option" do
  it "when disabled enables polyglot stdio" do
    ruby_exe("p Truffle::Boot.get_option('polyglot-stdio')", options: "--experimental-options --platform-native=false").should == "true\n"
  end

  it "when disabled can run basic expressions" do
    ruby_exe("p [1, 2, 3].map(&:succ)", options: "--experimental-options --platform-native=false").should == "[2, 3, 4]\n"
  end

  it "when disabled can use reasonable parts of the stdlib" do
    ruby_exe("require 'uri'; p URI('http://foo.com/posts?id=30&limit=5#time=1305298413').query", options: "--experimental-options --platform-native=false --disable-gems").should == "\"id=30&limit=5\"\n"
  end

  it "when disabled does not allow calling a native function given its address" do
    code = <<~RUBY
      require 'truffle/cext'
      begin
        Truffle::CExt.run_data_finalizer(0, 0, false)
      rescue SecurityError => e
        puts e.message
      end
    RUBY
    ruby_exe(code, options: "--experimental-options --platform-native=false").should == "native access is not allowed\n"
  end

  it "when disabled does not allow reading native memory given its address" do
    code = <<~RUBY
      require 'truffle/cext'
      begin
        Truffle::CExt.rb_ary_new_from_values(8, 1)
      rescue SecurityError => e
        puts e.message
      end
    RUBY
    ruby_exe(code, options: "--experimental-options --platform-native=false").should == "native access is not allowed\n"
  end

  it "when disabled does not allow registering a native unblocking function" do
    ruby_exe(fixture(__FILE__, 'native_access.rb'), args: 'unblocking_function', options: "--experimental-options --platform-native=false").should == "native access is not allowed\n"
  end

  it "when disabled does not allow evaluating an NFI signature" do
    ruby_exe(fixture(__FILE__, 'native_access.rb'), args: 'nfi', options: "--experimental-options --platform-native=false").should == "native access is not allowed\n"
  end

  it "when disabled does not allow sending a signal to the process" do
    ruby_exe(fixture(__FILE__, 'native_access.rb'), args: 'signal', options: "--experimental-options --platform-native=false").should == "native access is not allowed\n"
  end
end
