require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor#name" do
    it "returns nil if no name was given" do
      r = Ractor.new { }
      r.name.should == nil
      r.join
    end

    it "returns the name given to Ractor.new" do
      r = Ractor.new(name: "worker") { }
      r.name.should == "worker"
      r.join
    end

    it "returns nil for the main Ractor" do
      Ractor.main.name.should == nil
    end
  end
end
