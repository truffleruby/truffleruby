describe :ractor_port_send, shared: true do
  it "sends a message to the port and returns the port" do
    port = Ractor::Port.new
    port.__send__(@method, :message).should.equal?(port)
    port.receive.should == :message
  end

  it "queues the messages in order" do
    port = Ractor::Port.new
    port.__send__(@method, 1)
    port.__send__(@method, 2)
    port.__send__(@method, 3)
    3.times.map { port.receive }.should == [1, 2, 3]
  end

  it "can send from another Ractor" do
    port = Ractor::Port.new
    r = Ractor.new(port, @method) { |port, method| port.__send__(method, :from_ractor) }
    port.receive.should == :from_ractor
    r.join
  end

  it "raises Ractor::ClosedError if the port is closed" do
    port = Ractor::Port.new
    port.close
    -> { port.__send__(@method, :message) }.should.raise(Ractor::ClosedError, "The port was already closed")
  end
end
