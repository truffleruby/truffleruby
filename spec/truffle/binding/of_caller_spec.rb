# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Binding.of_caller" do

  def binding_of_caller
    Truffle::Binding.of_caller
  end

  def binding_of_caller_through_send
    Truffle::Binding.send(:of_caller)
  end

  #it "returns nil if there is no caller"
  #end

  it "returns a Binding" do
    binding_of_caller.should be_kind_of(Binding)
  end

  it "gives read access to local variables at the call site" do
    x = 14 # rubocop:disable Lint/UselessAssignment
    binding_of_caller.local_variable_get(:x).should == 14
  end

  it "gives write access to local variables at the call site" do
    x = 2
    binding_of_caller.local_variable_set(:x, 14)
    x.should == 14
  end

  it "works through #send" do
    x = 14 # rubocop:disable Lint/UselessAssignment
    binding_of_caller_through_send.local_variable_get(:x).should == 14
  end

end
