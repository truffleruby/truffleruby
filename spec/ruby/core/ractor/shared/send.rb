describe :ractor_send, shared: true do
  it "sends a message to the Ractor, which can receive it with Ractor.receive" do
    r = Ractor.new { Ractor.receive }
    r.__send__(@method, :message)
    r.value.should == :message
  end

  it "returns the Ractor" do
    r = Ractor.new { Ractor.receive }
    r.__send__(@method, :message).should.equal?(r)
    r.join
  end

  it "queues the messages in order" do
    r = Ractor.new { [Ractor.receive, Ractor.receive, Ractor.receive] }
    r.__send__(@method, 1)
    r.__send__(@method, 2)
    r.__send__(@method, 3)
    r.value.should == [1, 2, 3]
  end

  it "sends the message to the default port of the Ractor" do
    r = Ractor.new { Ractor.current.default_port.receive }
    r.__send__(@method, :message)
    r.value.should == :message
  end

  it "raises Ractor::ClosedError if the Ractor terminated" do
    r = Ractor.new { }
    r.join
    -> { r.__send__(@method, :message) }.should.raise(Ractor::ClosedError, "The port was already closed")
  end

  it "raises Ractor::ClosedError if the Ractor closed its default port" do
    r = Ractor.new do
      Ractor.current.close
      Ractor.main.send(:closed)
      Ractor.receive rescue :closed
    end
    Ractor.receive.should == :closed
    -> { r.__send__(@method, :message) }.should.raise(Ractor::ClosedError, "The port was already closed")
    r.value.should == :closed
  end
end
