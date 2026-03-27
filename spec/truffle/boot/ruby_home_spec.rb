# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Boot.ruby_home" do
  it "returns a String" do
    Truffle::Boot.ruby_home.should be_kind_of(String)
  end

  it "returns a path to a directory" do
    Dir.exist?(Truffle::Boot.ruby_home).should be_true
  end
end
