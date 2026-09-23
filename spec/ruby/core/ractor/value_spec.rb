require_relative '../../spec_helper'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby.
ruby_version_is "4.0" do
  describe "Ractor#value" do
    it "waits for the Ractor to terminate and returns the value of the block" do
      r = Ractor.new { Ractor.receive * 2 }
      r.send(21)
      r.value.should == 42
    end

    it "returns the same value when called again" do
      r = Ractor.new { :done }
      r.value.should == :done
      r.value.should == :done
    end

    it "raises Ractor::RemoteError if the Ractor terminated with an exception" do
      r = Ractor.new do
        Thread.current.report_on_exception = false
        raise "failed in Ractor"
      end
      -> { r.value }.should.raise(Ractor::RemoteError, "thrown by remote Ractor.") { |e|
        e.ractor.should.equal?(r)
        e.cause.should.is_a?(RuntimeError)
        e.cause.message.should == "failed in Ractor"
      }
    end

    it "raises Ractor::RemoteError with a SystemExit cause if the Ractor called exit" do
      r = Ractor.new { exit }
      -> { r.value }.should.raise(Ractor::RemoteError) { |e|
        e.cause.should.is_a?(SystemExit)
      }
    end
  end
end
