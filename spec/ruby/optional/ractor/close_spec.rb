require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor#close" do
    it "closes the default port of the current Ractor and returns it" do
      r = Ractor.new do
        port = Ractor.current.close
        [port.equal?(Ractor.current.default_port), port.closed?]
      end
      r.value.should == [true, true]
    end

    it "makes Ractor.receive raise Ractor::ClosedError" do
      r = Ractor.new do
        Ractor.current.close
        begin
          Ractor.receive
        rescue Ractor::ClosedError => e
          e.message
        end
      end
      r.value.should == "The port was already closed"
    end

    it "makes Ractor#send raise Ractor::ClosedError" do
      r = Ractor.new do
        Ractor.current.close
        Ractor.main.send(:closed)
        Ractor.receive rescue :closed
      end
      Ractor.receive.should == :closed
      -> { r.send(:message) }.should.raise(Ractor::ClosedError, "The port was already closed")
      r.value.should == :closed
    end

    it "raises Ractor::Error when called from another Ractor" do
      r = Ractor.new { Ractor.receive }
      -> { r.close }.should.raise(Ractor::Error, "closing port by other ractors is not allowed")
      r.send(:done)
      r.join
    end
  end
end
