require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor#unmonitor" do
    it "stops sending termination messages to the port and returns self" do
      port = Ractor::Port.new
      other = Ractor::Port.new
      r = Ractor.new { Ractor.receive }
      r.monitor(port)
      r.monitor(other)
      r.unmonitor(port).should.equal?(r)
      r.send(:done)
      other.receive.should == :exited
      r.join
      port.close
      -> { port.receive }.should.raise(Ractor::ClosedError)
    end

    it "returns self if the port was not monitoring the Ractor" do
      r = Ractor.new { Ractor.receive }
      r.unmonitor(Ractor::Port.new).should.equal?(r)
      r.send(:done)
      r.join
    end
  end
end
