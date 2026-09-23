require_relative '../../spec_helper'
require_relative 'shared/local_storage'

ruby_version_is "4.0" do
  describe "Ractor.[]=" do
    it_behaves_like :ractor_local_storage_set, :[]=
  end

  describe "Ractor#[]=" do
    it_behaves_like :ractor_local_storage_set, :[]=

    it "raises RuntimeError when called on a Ractor which is not the current Ractor" do
      r = Ractor.new do
        Thread.current.report_on_exception = false
        Ractor.main[:key] = 1
      end
      -> { r.value }.should.raise(Ractor::RemoteError) { |e|
        e.cause.should.is_a?(RuntimeError)
        e.cause.message.should == "Cannot set ractor local storage for non-current ractor"
      }
    end
  end
end
