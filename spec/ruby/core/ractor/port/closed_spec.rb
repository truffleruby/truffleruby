require_relative '../../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor::Port#closed?" do
    it "returns false for a new port" do
      Ractor::Port.new.closed?.should == false
    end

    it "returns true once the port is closed" do
      port = Ractor::Port.new
      port.close
      port.closed?.should == true
    end

    it "returns true for the default port of a terminated Ractor" do
      r = Ractor.new { }
      r.join
      Thread.pass until r.inspect.end_with?("terminated>")
      r.default_port.closed?.should == true
    end
  end
end
