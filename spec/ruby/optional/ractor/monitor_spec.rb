require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor#monitor" do
    it "sends :exited to the port when the Ractor terminates" do
      port = Ractor::Port.new
      r = Ractor.new { Ractor.receive }
      r.monitor(port).should == true
      r.send(:done)
      port.receive.should == :exited
      r.join
    end

    it "sends :aborted to the port when the Ractor terminates with an exception" do
      port = Ractor::Port.new
      r = Ractor.new do
        Thread.current.report_on_exception = false
        Ractor.receive
        raise "failed in Ractor"
      end
      r.monitor(port).should == true
      r.send(:done)
      port.receive.should == :aborted
      -> { r.join }.should.raise(Ractor::RemoteError)
    end

    it "returns false and sends :exited to the port immediately if the Ractor already terminated" do
      port = Ractor::Port.new
      r = Ractor.new { }
      r.join
      Thread.pass until r.inspect.end_with?("terminated>")
      r.monitor(port).should == false
      port.receive.should == :exited
    end

    it "returns false and sends :aborted to the port immediately if the Ractor already terminated with an exception" do
      port = Ractor::Port.new
      r = Ractor.new do
        Thread.current.report_on_exception = false
        raise "failed in Ractor"
      end
      -> { r.join }.should.raise(Ractor::RemoteError)
      Thread.pass until r.inspect.end_with?("terminated>")
      r.monitor(port).should == false
      port.receive.should == :aborted
    end

    it "sends a message to each monitoring port" do
      port1 = Ractor::Port.new
      port2 = Ractor::Port.new
      r = Ractor.new { Ractor.receive }
      r.monitor(port1)
      r.monitor(port2)
      r.send(:done)
      port1.receive.should == :exited
      port2.receive.should == :exited
      r.join
    end

    it "ignores a closed monitoring port" do
      port = Ractor::Port.new
      r = Ractor.new { Ractor.receive }
      r.monitor(port)
      port.close
      r.send(:done)
      r.value.should == :done
    end
  end
end
