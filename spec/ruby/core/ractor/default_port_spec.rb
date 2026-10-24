require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor#default_port" do
    it "returns a Ractor::Port" do
      Ractor.main.default_port.should.is_a?(Ractor::Port)
    end

    it "returns the same Ractor::Port each time" do
      Ractor.main.default_port.should.equal?(Ractor.main.default_port)
    end

    it "is the port receiving messages sent to the Ractor" do
      r = Ractor.new { Ractor.current.default_port.receive }
      r.send(:message)
      r.value.should == :message
    end

    it "is the port used by Ractor.receive" do
      r = Ractor.new { Ractor.receive }
      r.default_port.send(:message)
      r.value.should == :message
    end
  end
end
