require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor.count" do
    it "returns 1 when only the main Ractor exists" do
      Ractor.count.should == 1
    end

    it "counts the running Ractors including the main Ractor" do
      r = Ractor.new { Ractor.receive }
      Ractor.count.should == 2
      r.send(:done)
      r.join
      Thread.pass until r.inspect.end_with?("terminated>")
      Ractor.count.should == 1
    end
  end
end
