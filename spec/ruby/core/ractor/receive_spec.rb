require_relative '../../spec_helper'
require_relative 'shared/receive'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby.
ruby_version_is "4.0" do
  describe "Ractor.receive" do
    it_behaves_like :ractor_receive, :receive
  end

  describe "Ractor#receive" do
    it "is a private method" do
      Ractor.private_instance_methods(false).should.include?(:receive)
    end

    it "receives a message sent to the Ractor" do
      r = Ractor.new { receive }
      r.send(:message)
      r.value.should == :message
    end
  end
end
