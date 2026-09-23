require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#const_get" do
    before :each do
      @box = Ruby::Box.new
    end

    it "returns a constant of the box" do
      @box.require(fixture(__FILE__, "box_a.rb"))
      @box.const_get(:BoxSpecA).should == @box::BoxSpecA
      @box.const_get("BoxSpecA").should == @box::BoxSpecA
      @box.const_get("BoxSpecA::VERSION").should == "1.0"
      @box.const_get(:String).should == @box::String
      -> { @box.const_get(:BoxSpecUndefined) }.should.raise(NameError)
    end
  end
end
