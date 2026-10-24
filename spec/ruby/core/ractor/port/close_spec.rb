require_relative '../../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor::Port#close" do
    it "closes the port and returns it" do
      port = Ractor::Port.new
      port.close.should.equal?(port)
      port.closed?.should == true
    end

    it "makes #send raise Ractor::ClosedError" do
      port = Ractor::Port.new
      port.close
      -> { port.send(:message) }.should.raise(Ractor::ClosedError, "The port was already closed")
    end

    it "makes #receive raise Ractor::ClosedError" do
      port = Ractor::Port.new
      port.close
      -> { port.receive }.should.raise(Ractor::ClosedError, "The port was already closed")
    end

    it "can be called multiple times" do
      port = Ractor::Port.new
      port.close.should.equal?(port)
      port.close.should.equal?(port)
      port.closed?.should == true
    end
  end
end
