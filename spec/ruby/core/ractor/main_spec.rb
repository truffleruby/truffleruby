require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor.main" do
    it "returns the main Ractor" do
      Ractor.main.should.is_a?(Ractor)
      Ractor.main.should.equal?(Ractor.current)
    end

    it "returns the main Ractor from another Ractor" do
      r = Ractor.new { Ractor.main }
      r.value.should.equal?(Ractor.main)
    end
  end

  describe "Ractor.main?" do
    it "returns true in the main Ractor" do
      Ractor.main?.should == true
    end

    it "returns false in another Ractor" do
      Ractor.new { Ractor.main? }.value.should == false
    end
  end
end
