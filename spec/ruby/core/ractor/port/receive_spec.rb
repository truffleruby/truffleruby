require_relative '../../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor::Port#receive" do
    it "returns the messages sent to the port in order" do
      port = Ractor::Port.new
      port.send(1)
      port.send(2)
      port.receive.should == 1
      port.receive.should == 2
    end

    it "blocks until a message is sent to the port" do
      port = Ractor::Port.new
      r = Ractor.new(port) { |port| Ractor.receive; port.send(:message) }
      r.send(:go)
      port.receive.should == :message
      r.join
    end

    it "raises Ractor::ClosedError if the port is closed" do
      port = Ractor::Port.new
      port.close
      -> { port.receive }.should.raise(Ractor::ClosedError, "The port was already closed")
    end
  end
end
