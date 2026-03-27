# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

module TruffleThreadDetectRecursionSpecFixtures
  def self.check_recursion_to_depth(obj, depth)
    # checks that obj recurses to a given depth
    return false unless obj.respond_to?(:each)
    Truffle::ThreadOperations.detect_recursion(obj) do
      if depth > 1
        obj.each do |el|
          if check_recursion_to_depth(el, depth-1)
            return true
          end
        end
      end
    end
  end

  def self.check_double_recursion_equality_to_depth(obj1, obj2, depth)
    # checks that obj1 and obj2 are both recursive and equal structurally
    # (because detect_pair_recursion on two objects is only used during object comparison,
    # and aborts after inequality is discovered)
    return false unless obj1.class == obj2.class
    return false unless obj1.respond_to?(:each)
    return false unless obj1.size == obj2.size

    Truffle::ThreadOperations.detect_pair_recursion(obj1, obj2) do
      if depth > 1
        if obj1.class == Hash
          obj1.each do |key, val|
            return false unless obj2.has_key?(key)
            if check_double_recursion_equality_to_depth(val, obj2[key], depth-1)
              return true
            end
          end
        else
          obj1.size.times do |i|
            if check_double_recursion_equality_to_depth(obj1[i], obj2[i], depth-1)
              return true
            end
          end
        end
      end
    end
  end
end

describe "Thread#detect_recursion" do

  describe "for empty arrays" do
    it "returns false" do
      a = []
      10.times do |i|
        TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
      end
    end
  end

  describe "for empty hashes" do
    it "returns false" do
      a = {}
      10.times do |i|
        TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
      end
    end
  end

  describe "for single arrays" do
    it "for non-recursive arrays returns false" do
      a = [1,[2,[3], 4],[[[5,6,7]]]]

      10.times do |i|
        TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
      end
    end

    it "for recursive arrays returns true after sufficient depth to detect recursion" do
      a = []
      a << [[[a]]]

      b = []
      b << [1,[2,[3,b],4],5]

      10.times do |i|
        if i < 5
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(b, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_true
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(b, i).should be_true
        end
      end
    end
  end

  describe "for single hashes" do
    it "for non-recursive hashes returns false" do
      a = {:q => {:w => "qwe" }, :t => {:q => {:w => "qwe" }, :t => {:q => {:w => "qwe" }}}}

      10.times do |i|
        TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
      end
    end

    it "for recursive hashes returns true after sufficient depth to detect recursion" do
      a = {:q => {:w => "qwe" }}
      a[:t] = a

      10.times do |i|
        if i < 3
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_true
        end
      end
    end
  end

  describe "for single structs" do
    it "for recursive structs returns true after sufficient depth to detect recursion" do
      car = Struct.new(:make, :model, :year)
      a = car.new("Honda", "Accord", "1998")
      a[:make] = a

      10.times do |i|
        if i < 2
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_true
        end
      end
    end
  end

  describe "for single mixtures of types" do
    it "for recursive structs returns true after sufficient depth to detect recursion" do
      car = Struct.new(:make, :model, :year)
      a = car.new("Honda", "Accord", "1998")
      a[:make] = [{:car_make => ["a mess", {:here => a}]}]

      10.times do |i|
        if i < 8
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_recursion_to_depth(a, i).should be_true
        end
      end
    end
  end

  describe "for a pair of arrays" do
    it "returns false when structure differs" do
      a = []
      a << a

      c = [[[[[[[[[a,1]]]]]]]]]

      10.times do |i|
        TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, c, i).should be_false
      end
    end

    it "returns true after sufficient depth to detect recursion and equivalent structure" do
      a = []
      a << a

      b = []
      b << [[[[[[b]]]]]]

      10.times do |i|
        if i < 8
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_true
        end
      end
    end
  end

  describe "for a pair of hashes" do
    it "returns false when structure differs" do
      a = {:q => {:w => "qwe" }}
      a[:t] = a

      b = {:q => {:w => "qwe" }, :t => {:t => {:w => "qwe" }, :q => a}}

      10.times do |i|
        TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_false
      end
    end

    it "returns true after sufficient depth to detect recursion and equivalent structure" do
      a = {:q => {:w => "qwe" }}
      a[:t] = a

      b = {:q => {:w => "qwe" }, :t => {:q => {:w => "qwe" }, :t => a}}

      10.times do |i|
        if i < 4
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_true
        end
      end
    end
  end

  describe "for a pair of  structs" do
    it "returns true after sufficient depth to detect recursion and equivalent structure" do
      car = Struct.new(:make, :model, :year)
      a = car.new("Honda", "Accord", "1998")
      a[:make] = a
      b = car.new(a, "Accord", "1998")

      10.times do |i|
        if i < 3
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_true
        end
      end
    end
  end

  describe "for a pair of mixtures of types" do
    it "returns true after sufficient depth to detect recursion and equivalent structure" do
      car = Struct.new(:make, :model, :year)
      a = car.new("Honda", "Accord", "1998")
      a[:make] = [{:car_make => ["a mess", {:here => a}]}]
      b = car.new([{:car_make => ["a mess", {:here => a}]}], "Accord", "1998")

      20.times do |i|
        if i < 11
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_false
        else
          TruffleThreadDetectRecursionSpecFixtures.check_double_recursion_equality_to_depth(a, b, i).should be_true
        end
      end
    end
  end

end
