require_relative '../../spec_helper'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby. Whether an object is not shareable
# is not specified here, as an implementation without isolation treats every object as shareable.
ruby_version_is "4.0" do
  describe "Ractor.shareable?" do
    it "returns true for immediate values" do
      [1, 2**128, 1.5, :sym, nil, true, false].each do |obj|
        Ractor.shareable?(obj).should == true
      end
    end

    it "returns true for frozen Strings" do
      Ractor.shareable?("frozen".freeze).should == true
    end

    it "returns true for frozen Arrays and Hashes of shareable objects" do
      Ractor.shareable?([1, :sym].freeze).should == true
      Ractor.shareable?({ a: 1 }.freeze).should == true
    end

    it "returns true for Classes and Modules" do
      Ractor.shareable?(Object).should == true
      Ractor.shareable?(Kernel).should == true
    end

    it "returns true for Ractors" do
      Ractor.shareable?(Ractor.current).should == true
    end

    it "returns true for objects made shareable with Ractor.make_shareable" do
      Ractor.shareable?(Ractor.make_shareable(["a".dup])).should == true
    end
  end
end
