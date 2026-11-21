require_relative '../../spec_helper'
require_relative 'shared/receive'

ruby_version_is "4.0" do
  describe "Ractor.recv" do
    it_behaves_like :ractor_receive, :recv
  end

  describe "Ractor#recv" do
    it "is a private method" do
      Ractor.private_instance_methods(false).should.include?(:recv)
    end

    it "receives a message sent to the Ractor" do
      r = Ractor.new { recv }
      r.send(:message)
      r.value.should == :message
    end
  end
end
