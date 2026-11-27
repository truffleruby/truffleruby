require_relative 'spec_helper'

guard -> { ruby_box_enabled? } do
  describe "Ruby::Box#constants" do
    before :each do
      @box = Ruby::Box.new
    end

    it "returns the constants of the box" do
      @box.require(fixture(__FILE__, "box_a.rb"))
      constants = @box.constants.to_a.map(&:to_s)
      constants.should.include?("BoxSpecA")
      constants.should.include?("String")
      Object.constants.should_not.include?(:BoxSpecA)
    end
  end
end
