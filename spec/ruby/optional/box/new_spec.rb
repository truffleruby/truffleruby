require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box.new" do
    before :each do
      @box = Ruby::Box.new
    end

    it "creates a new user box" do
      @box.should.instance_of?(Ruby::Box)
      @box.main?.should == false
      @box.root?.should == false
    end

    it "creates independent boxes" do
      box2 = Ruby::Box.new
      @box.should_not == box2

      @box.eval("BOX_SPEC_ISOLATION = 'first'")
      box2.eval("BOX_SPEC_ISOLATION = 'second'")
      @box::BOX_SPEC_ISOLATION.should == "first"
      box2::BOX_SPEC_ISOLATION.should == "second"
      Object.const_defined?(:BOX_SPEC_ISOLATION).should == false
    end
  end
end
