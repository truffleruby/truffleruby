require_relative '../../spec_helper'
require_relative 'fixtures/classes'

describe "Hash#replace" do
  it "replaces the contents of self with other" do
    h = { a: 1, b: 2 }
    h.replace(c: -1, d: -2).should equal(h)
    h.should == { c: -1, d: -2 }
  end

  it "tries to convert the passed argument to a hash using #to_hash" do
    obj = mock('{1=>2,3=>4}')
    obj.should_receive(:to_hash).and_return({ 1 => 2, 3 => 4 })

    h = {}
    h.replace(obj)
    h.should == { 1 => 2, 3 => 4 }
  end

  it "calls to_hash on hash subclasses" do
    h = {}
    h.replace(HashSpecs::ToHashHash[1 => 2])
    h.should == { 1 => 2 }
  end

  it "transfers compare_by_identity flag of an argument" do
    h = { a: 1, c: 3 }
    h2 = { b: 2, d: 4 }.compare_by_identity
    h.replace(h2)
    h.compare_by_identity?.should == true
  end

  it "does not retain compare_by_identity flag" do
    h = { a: 1, c: 3 }.compare_by_identity
    h.replace(b: 2, d: 4)
    h.compare_by_identity?.should == false
  end

  it "does not transfer default values" do
    hash_a = {}
    hash_b = Hash.new(5)
    hash_a.replace(hash_b)
    hash_a.default.should == 5

    hash_a = {}
    hash_b = Hash.new { |h, k| k * 2 }
    hash_a.replace(hash_b)
    hash_a.default(5).should == 10

    hash_a = Hash.new { |h, k| k * 5 }
    hash_b = Hash.new(-> { raise "Should not invoke lambda" })
    hash_a.replace(hash_b)
    hash_a.default.should == hash_b.default
  end

  it "raises a FrozenError if called on a frozen instance that would not be modified" do
    -> do
      HashSpecs.frozen_hash.replace(HashSpecs.frozen_hash)
    end.should raise_error(FrozenError)
  end

  it "raises a FrozenError if called on a frozen instance that is modified" do
    -> do
      HashSpecs.frozen_hash.replace(HashSpecs.empty_frozen_hash)
    end.should raise_error(FrozenError)
  end
end
