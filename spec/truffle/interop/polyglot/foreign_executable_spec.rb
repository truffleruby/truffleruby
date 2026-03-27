# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignExecutable" do
  before :each do
    @foreign = Truffle::Debug.foreign_executable(42)
  end

  it "supports #call" do
    @foreign.call.should == 42
  end

  it "supports #to_proc" do
    @foreign.to_proc.should.is_a?(Proc)
    @foreign.to_proc.call.should == 42
  end

  it "supports being passed as a block via foo(&foreign)" do
    def y
      yield
    end

    y(&@foreign).should == 42
  end
end
