require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor#join" do
    it "waits for the Ractor to terminate and returns self" do
      r = Ractor.new { Ractor.receive }
      r.send(:done)
      r.join.should.equal?(r)
      Thread.pass until r.inspect.end_with?("terminated>")
    end

    it "can be called multiple times" do
      r = Ractor.new { }
      r.join.should.equal?(r)
      r.join.should.equal?(r)
    end

    it "raises Ractor::RemoteError if the Ractor terminated with an exception" do
      r = Ractor.new do
        Thread.current.report_on_exception = false
        raise "failed in Ractor"
      end
      -> { r.join }.should.raise(Ractor::RemoteError, "thrown by remote Ractor.") { |e|
        e.ractor.should.equal?(r)
        e.cause.should.is_a?(RuntimeError)
        e.cause.message.should == "failed in Ractor"
      }
    end
  end
end
