require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#const_defined?" do
    before :each do
      @box = Ruby::Box.new
    end

    it "returns whether the constant is defined in the box" do
      @box.const_defined?(:BoxSpecA).should == false
      @box.require(fixture(__FILE__, "box_a.rb"))
      @box.const_defined?(:BoxSpecA).should == true
      @box.const_defined?("BoxSpecA::VERSION").should == true
      @box.const_defined?(:String).should == true
    end
  end
end
