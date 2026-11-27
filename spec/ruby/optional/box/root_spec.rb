require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box.root" do
    it "returns the root box" do
      Ruby::Box.root.should.instance_of?(Ruby::Box)
      Ruby::Box.root.root?.should == true
      Ruby::Box.root.main?.should == false
      Ruby::Box.root.should == Ruby::Box.root
      Ruby::Box.root.should_not == Ruby::Box.main
    end

    it "is isolated from the main box" do
      $box_spec_root_gvar = 0
      Ruby::Box.root.eval("$box_spec_root_gvar = 1; $box_spec_root_gvar").should == 1
      $box_spec_root_gvar.should == 0

      Ruby::Box.root.eval("class BoxSpecRootClass; end")
      Object.const_defined?(:BoxSpecRootClass).should == false
      Ruby::Box.root.eval("Object.const_defined?(:BoxSpecRootClass)").should == true

      Ruby::Box.root.eval("Ruby::Box.current.root?").should == true
    end
  end
end
