describe :ractor_inspect, shared: true do
  it "returns the id and the status for the main Ractor" do
    Ractor.main.__send__(@method).should == "#<Ractor:#1 running>"
  end

  it "returns the id, the location of the block and the status" do
    r = Ractor.new { Ractor.receive }
    r.__send__(@method).should =~ /\A#<Ractor:#\d+ #{Regexp.escape(__FILE__)}:#{__LINE__ - 1} (running|blocking)>\z/
    r.send(:done)
    r.join
  end

  it "includes the name of the Ractor" do
    r = Ractor.new(name: "worker") { Ractor.receive }
    r.__send__(@method).should =~ /\A#<Ractor:#\d+ worker #{Regexp.escape(__FILE__)}:#{__LINE__ - 1} (running|blocking)>\z/
    r.send(:done)
    r.join
  end

  it "shows the terminated status once the Ractor terminated" do
    r = Ractor.new { }
    r.join
    Thread.pass until r.__send__(@method).end_with?("terminated>")
    r.__send__(@method).should =~ /\A#<Ractor:#\d+ #{Regexp.escape(__FILE__)}:#{__LINE__ - 3} terminated>\z/
  end
end
