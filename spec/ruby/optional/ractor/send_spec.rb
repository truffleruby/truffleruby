require_relative '../../spec_helper'
require_relative 'shared/send'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby.
ruby_version_is "4.0" do
  describe "Ractor#send" do
    it_behaves_like :ractor_send, :send
  end
end
