require_relative '../../spec_helper'
require 'pp'

describe "Set#pretty_print" do
  ruby_version_is "4.0" do
    it "uses the same format as Set#inspect" do
      Set[].pretty_inspect.should == "Set[]\n"
      Set[1, 2, 3].pretty_inspect.should == "Set[1, 2, 3]\n"
      Set[1, Set[2]].pretty_inspect.should == "Set[1, Set[2]]\n"
    end

    it "uses the Set[...] format for a self-referencing Set" do
      set = Set[]
      set << set
      set.pretty_inspect.should == "Set[Set[...]]\n"
    end
  end

  ruby_version_is ""..."4.0" do
    it "uses the #<Set: {...}> format" do
      Set[1, 2, 3].pretty_inspect.should == "#<Set: {1, 2, 3}>\n"
    end
  end
end
