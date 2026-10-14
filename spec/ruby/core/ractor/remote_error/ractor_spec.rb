require_relative '../../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor::RemoteError#ractor" do
    it "returns the Ractor which terminated with an exception" do
      r = Ractor.new do
        Thread.current.report_on_exception = false
        raise "failed in Ractor"
      end
      -> { r.value }.should.raise(Ractor::RemoteError) { |e|
        e.ractor.should.equal?(r)
      }
    end
  end
end
