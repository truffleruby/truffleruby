require_relative '../../spec_helper'
require_relative 'shared/local_storage'

ruby_version_is "4.0" do
  describe "Ractor.[]" do
    it_behaves_like :ractor_local_storage_get, :[]
  end

  describe "Ractor#[]" do
    it_behaves_like :ractor_local_storage_get, :[]

    it "raises RuntimeError when called on a Ractor which is not the current Ractor" do
      r = Ractor.new do
        Thread.current.report_on_exception = false
        Ractor.main[:key]
      end
      -> { r.value }.should.raise(Ractor::RemoteError) { |e|
        e.cause.should.is_a?(RuntimeError)
        e.cause.message.should == "Cannot get ractor local storage for non-current ractor"
      }
    end
  end
end
