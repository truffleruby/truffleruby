require_relative '../../spec_helper'
require_relative 'fixtures/classes'
require_relative '../enumerable/shared/enumeratorized'

# Modifying a collection while the contents are being iterated
# gives undefined behavior. See
# https://blade.ruby-lang.org/ruby-core/23633

ruby_version_is "4.0" do
  describe "Array#rfind" do
    it "returns the last element for which the block returns a truthy value" do
      [1, 2, 3, 4, 5].rfind { |x| x % 2 == 0 }.should == 4
    end

    it "returns nil when no element matches and no ifnone proc is given" do
      [1, 2, 3].rfind { |x| false }.should == nil
    end

    it "calls the ifnone proc and returns its value when no element matches" do
      fail_proc = -> { "cheeseburgers" }
      [1, 2, 3].rfind(fail_proc) { |x| false }.should == "cheeseburgers"
    end

    it "does not call the ifnone proc when an element is found" do
      fail_proc = -> { raise "This shouldn't have been called" }
      [1, 2, 3].rfind(fail_proc) { |x| x == 3 }.should == 3
    end

    it "iterates elements in reverse order" do
      visited = []
      [1, 2, 3].rfind { |x| visited << x; false }
      visited.should == [3, 2, 1]
    end

    it "stops iterating as soon as a matching element is found from the end" do
      visited = []
      [1, 2, 3, 4, 5].rfind { |x| visited << x; x == 3 }
      visited.should == [5, 4, 3]
    end

    it "returns an Enumerator when no block is given" do
      [1, 2, 3].rfind.should.instance_of?(Enumerator)
    end

    it "rechecks the array size during iteration" do
      ary = [4, 2, 1, 5, 1, 3]
      seen = []
      ary.rfind { |x| seen << x; ary.clear; false }
      seen.should == [3]
    end

    it_behaves_like :enumeratorized_with_unknown_size, :rfind, [1, 2, 3]
  end
end
