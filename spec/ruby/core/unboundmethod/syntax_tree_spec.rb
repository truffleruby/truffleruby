require_relative '../../spec_helper'
require_relative '../method/shared/syntax_tree'

ruby_version_is "4.1" do
  describe "UnboundMethod#syntax_tree" do
    before :each do
      @object = -> method { method.unbind }
    end

    it_behaves_like :method_syntax_tree, :syntax_tree
  end
end
