require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#load_path" do
    before :each do
      @box = Ruby::Box.new
    end

    it "returns the $LOAD_PATH of the box" do
      @box.load_path.should_not.equal?($LOAD_PATH)
      @box.load_path.to_a.should == @box.eval("$LOAD_PATH").to_a
    end
  end
end
