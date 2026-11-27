require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box.current" do
    before :each do
      @box = Ruby::Box.new
    end

    it "returns the main box at the top-level" do
      Ruby::Box.current.should == Ruby::Box.main
      Ruby::Box.current.main?.should == true
    end

    it "returns the box in which the code runs" do
      @box.should == @box.eval("Ruby::Box.current")
      @box.eval("Ruby::Box.current.main?").should == false
      @box.eval("Ruby::Box.current.root?").should == false
      @box.eval("Ruby::Box.current.inspect").to_s.should =~ /\A#<Ruby::Box:\d+,user,optional>\z/
    end
  end
end
