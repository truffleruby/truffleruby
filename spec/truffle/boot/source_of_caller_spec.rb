# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Boot.source_of_caller" do

  def source_of_caller
    Truffle::Boot.source_of_caller
  end

  def source_of_caller_through_send
    Truffle::Boot.send(:source_of_caller)
  end

  #it "returns nil if there is no caller"
  #end

  it "returns a String" do
    source_of_caller.should be_kind_of(String)
  end

  it "returns the name of the file at the call site" do
    source_of_caller.should == __FILE__
  end

  it "works through #send" do
    source_of_caller_through_send.should == __FILE__
  end

end
