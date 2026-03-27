# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Integer#<<" do
  it "can shift an int by a long" do
    (10 << Truffle::Debug.long(10)).should == 10240
    (10240 << -Truffle::Debug.long(10)).should == 10
  end

  it "can shift an long by a int" do
    (Truffle::Debug.long(10) << 10).should == 10240
    (Truffle::Debug.long(10240) << -10).should == 10
  end

  it "can shift a long by a long" do
    (Truffle::Debug.long(10) << Truffle::Debug.long(10)).should == 10240
    (Truffle::Debug.long(10240) << Truffle::Debug.long(-10)).should == 10
  end
end
