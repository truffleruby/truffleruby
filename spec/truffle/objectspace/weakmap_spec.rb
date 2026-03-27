# truffleruby_primitives: true
# rubocop:disable Lint/UselessAssignment
#   (to nil out references to make unreachable)

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "ObjectSpace::WeakMap" do

  it "gets rid of unreferenced objects" do
    map = ObjectSpace::WeakMap.new
    key = "a".upcase
    ref = "x"
    map[key] = ref
    ref = nil

    Primitive.gc_force

    map[key].should == nil
    map.key?(key).should == false
    map.include?(key).should == false
    map.member?(key).should == false
    map.values.should == []
    map.keys.should == []
    map.size.should == 0
    map.length.should == 0
  end

  it "has iterators methods that exclude unreferenced objects" do

    # This spec does not pass on MRI because the garbage collector is presumably too conservative and will not get rid
    # of the references eagerly enough.

    map = ObjectSpace::WeakMap.new
    k1, k2 = %w[a b].map(&:upcase)
    # Interestingly, `v1, v2 = %w[x y].map &:upcase` causes the tests to fail.
    v1 = "x".upcase
    v2 = "y".upcase
    map[k1] = v1
    map[k2] = v2
    v2 = nil

    Primitive.gc_force

    map.key?(k2).should == false

    a = []
    map.each { |k,v| a << "#{k}#{v}" }
    a.should == ["AX"]

    a = []
    map.each_pair { |k,v| a << "#{k}#{v}" }
    a.should == ["AX"]

    a = []
    map.each_pair { |k,v| a << "#{k}#{v}" }
    a.should == ["AX"]

    a = []
    map.each_key { |k| a << k }
    a.should == ["A"]

    a = []
    map.each_value { |v| a << v }
    a.should == ["X"]
  end

  it "supports constant objects" do
    map = ObjectSpace::WeakMap.new
    map[1] = 2
    Primitive.gc_force
    map[1].should == 2
  end

  it "supports frozen objects" do
    map = ObjectSpace::WeakMap.new
    x = "x".freeze
    y = "y".freeze
    map[x] = y
    Primitive.gc_force
    map[x].should == y
  end
end
