# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.execute" do

  def add(a, b)
    a + b
  end

  it "calls methods" do
    Truffle::Interop.execute(method(:add), 14, 2).should == 16
  end

  it "calls procs" do
    Truffle::Interop.execute(proc { |a, b| a + b }, 14, 2).should == 16
  end

  it "calls lambdas" do
    Truffle::Interop.execute(-> a, b { a + b }, 14, 2).should == 16
  end

  it "doesn't call nil" do
    -> { Truffle::Interop.execute(nil) }.should raise_error Polyglot::UnsupportedMessageError
  end

  it "doesn't call strings" do
    -> { Truffle::Interop.execute('hello') }.should raise_error Polyglot::UnsupportedMessageError
  end

end
