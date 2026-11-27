require_relative '../../../spec_helper'
require_relative 'shared/send'

ruby_version_is "4.0" do
  describe "Ractor::Port#<<" do
    it_behaves_like :ractor_port_send, :<<
  end
end
