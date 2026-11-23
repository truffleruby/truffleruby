# -*- encoding: us-ascii -*-

require_relative '../../../spec_helper'
require_relative 'fixtures/classes'

describe "Enumerator::Lazy#filter_map" do
  it "maps only truthy results" do
    (1..Float::INFINITY).lazy.filter_map { |i| i if i.odd? }.first(4).should == [1, 3, 5, 7]
  end

  it "does not map false results" do
    (1..Float::INFINITY).lazy.filter_map { |i| i.odd? ? i : false }.first(4).should == [1, 3, 5, 7]
  end

  it "passes a source yield with no value on to the block as nil" do
    args = nil
    Enumerator.new { |y| y.yield }.lazy.filter_map { |*a| args = a }.force
    args.should == [nil]
  end

  it "passes every value of a multi-value source yield on to the block" do
    args = nil
    Enumerator.new { |y| y.yield 1, 2 }.lazy.filter_map { |*a| args = a }.force
    args.should == [1, 2]
  end
end
