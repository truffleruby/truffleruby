require_relative '../../spec_helper'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby.
ruby_version_is "4.0" do
  describe "Ractor.select" do
    it "returns the port and the message when a message is sent to the given port" do
      port = Ractor::Port.new
      r = Ractor.new(port) { |port| port.send(:message) }
      Ractor.select(port).should == [port, :message]
      r.join
    end

    it "returns the Ractor and its value when the given Ractor terminates" do
      r = Ractor.new { Ractor.receive }
      r.send(:message)
      Ractor.select(r).should == [r, :message]
    end

    it "waits until a message is sent to any of the given ports" do
      port1 = Ractor::Port.new
      port2 = Ractor::Port.new
      r = Ractor.new(port1, port2) do |port1, port2|
        Ractor.receive
        port2.send(:message2)
        Ractor.receive
        port1.send(:message1)
      end

      r.send(:go)
      Ractor.select(port1, port2).should == [port2, :message2]
      r.send(:go)
      Ractor.select(port1, port2).should == [port1, :message1]
      r.join
    end

    it "accepts both Ractor::Port and Ractor" do
      port = Ractor::Port.new
      r = Ractor.new(port) do |port|
        Ractor.receive
        port.send(:from_port)
        Ractor.receive
        :from_ractor
      end

      r.send(:go)
      Ractor.select(port, r).should == [port, :from_port]
      r.send(:go)
      Ractor.select(port, r).should == [r, :from_ractor]
    end

    it "receives every message sent to the ports" do
      ports = 3.times.map { Ractor::Port.new }
      r = Ractor.new(ports) do |ports|
        ports.each_with_index { |port, i| port.send(i) }
      end

      received = 3.times.map { Ractor.select(*ports) }
      received.sort_by { |_port, i| i }.should == ports.each_with_index.to_a
      r.join
    end

    it "raises ArgumentError if no argument is given" do
      -> { Ractor.select }.should.raise(ArgumentError, "specify at least one Ractor::Port or Ractor")
    end

    it "raises ArgumentError if an argument is not a Ractor::Port or a Ractor" do
      -> { Ractor.select(Ractor::Port.new, 42) }.should.raise(ArgumentError, "should be Ractor::Port or Ractor")
    end

    it "raises Ractor::ClosedError if a port is closed" do
      port = Ractor::Port.new
      port.close
      -> { Ractor.select(port) }.should.raise(Ractor::ClosedError, "The port was already closed")
    end
  end
end
