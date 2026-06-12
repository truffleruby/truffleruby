require_relative '../../spec_helper'
require_relative 'fixtures/classes'
require_relative '../enumerable/shared/enumeratorized'

ruby_version_is "4.0" do
  describe "Array#find" do
    it "returns the first element for which the block returns a truthy value" do
      [1, 2, 3, 4, 5].find { |x| x % 2 == 0 }.should == 2
    end

    it "returns nil when no element matches and no ifnone proc is given" do
      [1, 2, 3].find { |x| false }.should == nil
    end

    it "calls the ifnone proc and returns its value when no element matches" do
      fail_proc = -> { "cheeseburgers" }
      [1, 2, 3].find(fail_proc) { |x| false }.should == "cheeseburgers"
    end

    it "does not call the ifnone proc when an element is found" do
      fail_proc = -> { raise "This shouldn't have been called" }
      [1, 2, 3].find(fail_proc) { |x| x == 1 }.should == 1
    end

    it "stops iterating as soon as an element is found" do
      visited = []
      [1, 2, 3, 4, 5].find { |x| visited << x; x == 3 }
      visited.should == [1, 2, 3]
    end

    it "returns an Enumerator when no block is given" do
      [1, 2, 3].find.should.instance_of?(Enumerator)
    end

    it "is also available as #detect using the Array-specific implementation" do
      [].method(:detect).owner.should == Array
      [1, 2, 3].detect { |x| x % 2 == 0 }.should == 2
    end

    it_behaves_like :enumeratorized_with_unknown_size, :find, [1, 2, 3]
  end
end
