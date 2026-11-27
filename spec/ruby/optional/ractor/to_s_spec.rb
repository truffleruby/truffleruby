require_relative '../../spec_helper'
require_relative 'shared/inspect'

ruby_version_is "4.0" do
  describe "Ractor#to_s" do
    it_behaves_like :ractor_inspect, :to_s
  end
end
