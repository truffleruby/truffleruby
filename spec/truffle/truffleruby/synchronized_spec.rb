# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "TruffleRuby.synchronized" do

  it "yields to the block" do
    object = Object.new
    a = 1
    TruffleRuby.synchronized(object) do
      a = 2
    end
    a.should == 2
  end

  it "is re-entrant" do
    object = Object.new
    a = 1
    TruffleRuby.synchronized(object) do
      TruffleRuby.synchronized(object) do
        a = 2
      end
    end
    a.should == 2
  end

  it "cannot be used with a primitive" do
    [true, 14, 14.2].each do |primitive|
      -> {
        TruffleRuby.synchronized(primitive) { }
      }.should raise_error(TypeError)
    end
  end

end
