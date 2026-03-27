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
  describe "can correctly parse" do
    it "booleans" do
      ruby_exe("p Truffle::Boot.get_option('frozen-string-literals')").should_not == "true\n"
      ruby_exe("p Truffle::Boot.get_option('frozen-string-literals')", options: "--experimental-options --frozen-string-literals=true").should == "true\n"
      ruby_exe("p Truffle::Boot.get_option('frozen-string-literals')", options: "--experimental-options --frozen-string-literals").should == "true\n"
    end

    it "integers" do
      ruby_exe("p Truffle::Boot.get_option('default-cache')").should_not == "99\n"
      ruby_exe("p Truffle::Boot.get_option('default-cache')", options: "--experimental-options --default-cache=99").should == "99\n"
    end

    it "enum values" do
      ruby_exe("p Truffle::Boot.get_option('verbose')").should_not == ":nil\n"
      ruby_exe("p Truffle::Boot.get_option('verbose')", options: "--verbose=nil").should == ":nil\n" # TODO
      ruby_exe("p Truffle::Boot.get_option('verbose')", options: "--verbose").should == ":true\n"
    end

    it "strings" do
      ruby_exe("p Truffle::Boot.get_option('launcher')", options: "--experimental-options --launcher=ruby_spec_test_value").should == "\"ruby_spec_test_value\"\n"
      ruby_exe("p Truffle::Boot.get_option('launcher')", options: "--experimental-options '--launcher=ruby spec test value with spaces'").should == "\"ruby spec test value with spaces\"\n"
    end

    it "arrays of strings" do
      ruby_exe("p Truffle::Boot.get_option('load-paths')", options: "--load-paths=ruby_spec_test_value").should == "[\"ruby_spec_test_value\"]\n"
      ruby_exe("p Truffle::Boot.get_option('load-paths')", options: "--load-paths=a,b,c").should == "[\"a\", \"b\", \"c\"]\n"
      ruby_exe("p Truffle::Boot.get_option('load-paths')", options: "--load-paths=a\\\\,b,c").should == "[\"a,b\", \"c\"]\n"
    end
  end

  describe "handles parsing errors with" do
    it "booleans" do
      ruby_exe("14", options: "--frozen-string-literals=foo", args: "2>&1", exit_status: 1).should.include?("Invalid boolean option value 'foo'")
    end

    it "integers" do
      ruby_exe("14", options: "--default-cache=foo", args: "2>&1", exit_status: 1).should.include?("Invalid argument --default-cache=foo specified")
    end

    it "enum values" do
      ruby_exe("14", options: "--verbose=foo", args: "2>&1", exit_status: 1).should.include?("ERROR: Invalid argument --verbose=foo specified. Invalid option value 'foo'. Valid options values are: 'nil', 'false', 'true'")
    end
  end
end
