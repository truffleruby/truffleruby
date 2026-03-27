# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'
require 'rbconfig'

describe "RbConfig.ruby" do
  it "returns a String" do
    RbConfig.ruby.should be_kind_of(String)
  end

  it "returns a path to an executable" do
    File.executable?(RbConfig.ruby).should == true
  end

  it "can be used to run a TruffleRuby subprocess" do
    launcher = RbConfig.ruby
    `#{launcher} -e "puts RUBY_ENGINE" 2>&1`.lines.last.chomp.should == RUBY_ENGINE
    $?.success?.should == true
  end
end
