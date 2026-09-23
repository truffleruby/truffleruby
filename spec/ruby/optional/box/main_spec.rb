require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box.main" do
    it "returns the main box" do
      Ruby::Box.main.should.instance_of?(Ruby::Box)
      Ruby::Box.main.main?.should == true
      Ruby::Box.main.root?.should == false
      Ruby::Box.main.should == Ruby::Box.main
    end

    it "evaluates code in the main box" do
      Ruby::Box.main.eval("1 + 1").should == 2
      Ruby::Box.main.eval("Ruby::Box.current.main?").should == true
      Ruby::Box.main.eval("$box_spec_main_gvar = 42")
      $box_spec_main_gvar.should == 42
    end
  end
end
