describe :ractor_receive, shared: true do
  it "receives a message sent to the current Ractor" do
    r = Ractor.new(@method) { |method| Ractor.__send__(method) }
    r.send(:message)
    r.value.should == :message
  end

  it "receives the messages in the order they were sent" do
    r = Ractor.new(@method) { |method| [Ractor.__send__(method), Ractor.__send__(method)] }
    r.send(1)
    r.send(2)
    r.value.should == [1, 2]
  end

  it "blocks until a message is sent" do
    r = Ractor.new(@method) { |method| Ractor.main.send(:started); Ractor.__send__(method) }
    Ractor.__send__(@method).should == :started
    r.inspect.should_not.end_with?("terminated>")
    r.send(:message)
    r.value.should == :message
  end

  it "receives messages sent to the main Ractor" do
    r = Ractor.new { Ractor.main.send(:from_ractor) }
    Ractor.__send__(@method).should == :from_ractor
    r.join
  end

  it "raises Ractor::ClosedError if the default port of the current Ractor is closed" do
    r = Ractor.new(@method) do |method|
      Ractor.current.close
      begin
        Ractor.__send__(method)
      rescue Ractor::ClosedError => e
        e.message
      end
    end
    r.value.should == "The port was already closed"
  end
end
