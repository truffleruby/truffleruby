# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop source messages" do
  it "returns correct source location" do
    method = Array.instance_method(:each_index)
    Truffle::Interop.has_source_location?(method).should == true
    Truffle::Interop.source_location(method).path.should.end_with?("/array.rb")

    Truffle::Interop.has_source_location?(Array).should == true
    Truffle::Interop.source_location(Array).should_not.available?
    Truffle::Interop.source_location(Array).path.should == "(unknown)"
  end
end
