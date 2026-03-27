# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

require 'digest'

module TruffleDebugSpecFixtures

  def self.subject(a, b)
    c = a + b
    c * 2
  end

  ADD_LINE = 16
  MUL_LINE = 17

end

describe "Truffle::Debug" do

  it "can add and remove breakpoints" do
    breaks = []

    breakpoint = Truffle::Debug.break __FILE__, TruffleDebugSpecFixtures::ADD_LINE do |_binding|
      breaks << :break
    end

    TruffleDebugSpecFixtures.subject(14, 2)
    TruffleDebugSpecFixtures.subject(16, 4)

    breakpoint.remove

    TruffleDebugSpecFixtures.subject(18, 9)

    breaks.should == [:break, :break]
  end

  it "can observe local variables in a breakpoint" do
    breaks = []

    breakpoint1 = Truffle::Debug.break __FILE__, TruffleDebugSpecFixtures::ADD_LINE do |binding|
      breaks << binding.local_variable_get(:a) << binding.local_variable_get(:b)
    end

    breakpoint2 = Truffle::Debug.break __FILE__, TruffleDebugSpecFixtures::MUL_LINE do |binding|
      breaks << binding.local_variable_get(:c)
    end

    TruffleDebugSpecFixtures.subject(14, 2)
    TruffleDebugSpecFixtures.subject(16, 4)

    breakpoint1.remove
    breakpoint2.remove

    TruffleDebugSpecFixtures.subject(18, 9)

    breaks.should == [14, 2, 14 + 2, 16, 4, 16 + 4]
  end


end
