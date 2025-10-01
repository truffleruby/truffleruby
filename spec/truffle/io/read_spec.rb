# Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "IO#read(n)" do
  before :each do
    @r, @w = IO.pipe
  end

  after :each do
    @r.close
    @w.close
  end

  # https://github.com/truffleruby/truffleruby/issues/1951
  it "only reads the requested bytes and does not buffer more" do
    r, w = @r, @w
    # To make sure it works regardless of sync
    r.sync = false
    w.sync = false

    w.write "a"
    w.write "b"
    w.flush

    select_ignoring_iobuffer([r], 100).should == [r]
    r.read(1).should == "a"

    select_ignoring_iobuffer([r], 1000).should == [r]
    r.read(1).should == "b"
  end

  it "#select_ignoring_iobuffer does not see data in the IO buffer" do
    @w.write "a\n"
    @w.write "b\n"
    @w.flush

    @r.gets.should == "a\n"
    select_ignoring_iobuffer([@r], 100).should == nil
  end

  def select_ignoring_iobuffer(ios, timeout_ms)
    return IO.select(ios)[0] unless defined?(::TruffleRuby)

    result = Truffle::IOOperations.select(
      ios, ios,
      [], [],
      [], [],
      timeout_ms)
    result and result[0]
  end
end
