require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#inspect" do
    before :each do
      @box = Ruby::Box.new
    end

    it "returns a String describing the box" do
      @box.inspect.should =~ /\A#<Ruby::Box:\d+,user,optional>\z/
      Ruby::Box.main.inspect.should =~ /\A#<Ruby::Box:\d+,user,main>\z/
      Ruby::Box.root.inspect.should =~ /\A#<Ruby::Box:\d+,root>\z/
    end
  end
end
