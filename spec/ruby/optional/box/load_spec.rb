require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#load" do
    before :each do
      @box = Ruby::Box.new
    end

    it "loads the file in the box each time and returns true" do
      path = fixture(__FILE__, "load_count.rb")
      @box.load(path).should == true
      @box.eval("$box_spec_load_count").should == 1
      @box.load(path).should == true
      @box.eval("$box_spec_load_count").should == 2
      $box_spec_load_count.should.nil?
    end
  end
end
