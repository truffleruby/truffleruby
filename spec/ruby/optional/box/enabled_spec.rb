require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box.enabled?" do
    it "returns true when Ruby::Box is enabled" do
      Ruby::Box.enabled?.should == true
    end
  end
end
