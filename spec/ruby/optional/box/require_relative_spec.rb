require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#require_relative" do
    before :each do
      @box = Ruby::Box.new
    end

    it "requires the file relative to the caller in the box" do
      @box.require_relative("fixtures/box_a").should == true
      @box::BoxSpecA::VERSION.should == "1.0"
      Object.const_defined?(:BoxSpecA).should == false
    end
  end
end
