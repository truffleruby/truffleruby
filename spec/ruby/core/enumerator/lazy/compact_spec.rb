require_relative '../../../spec_helper'
require_relative 'fixtures/classes'

describe "Enumerator::Lazy#compact" do
  # Cannot use shared/value_packing.rb examples: the packed nil is removed by #compact.
  describe "value packing of source yields" do
    it "packs a multi-argument source yield into an Array" do
      e = Enumerator.new { |y| y.yield 1, 2 }
      args = nil
      e.lazy.compact.each { |*a| args = a }
      args.should == [[1, 2]]
    end

    it "removes a source yield with no value like nil" do
      e = Enumerator.new { |y| y.yield; y.yield :v }
      collected = []
      e.lazy.compact.each { |*a| collected << a }
      collected.should == [[:v]]
    end
  end

  it 'returns array without nil elements' do
    arr = [1, nil, 3, false, 5].to_enum.lazy.compact
    arr.should.instance_of?(Enumerator::Lazy)
    arr.force.should == [1, 3, false, 5]
  end

  it "sets #size to nil" do
    Enumerator::Lazy.new(Object.new, 100) {}.compact.size.should == nil
  end

  # Cannot use shared/value_propagation.rb wholesale: #compact removes the
  # packed nil, so neither YieldsMixed nor a yield with no value survives it.
  describe "propagating source yields to later methods in the chain" do
    it "passes every value of a multi-value source yield to a splat block later in the chain" do
      args = nil
      Enumerator.new { |y| y.yield 1, 2 }.lazy.compact.map { |*a| args = a }.force
      args.should == [1, 2]
    end

    it "yields a single value once a method in the chain replaces the value" do
      yields = []
      Enumerator.new { |y| y.yield 1, 2 }.lazy.compact.map { |x| x }.map { |*a| yields << a }.force
      yields.should == [[1]]
    end
  end
end
