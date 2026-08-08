require_relative '../../spec_helper'
require_relative 'shared/syntax_tree'

ruby_version_is "4.1" do
  describe "Method#syntax_tree" do
    before :each do
      @object = -> method { method }
    end

    it_behaves_like :method_syntax_tree, :syntax_tree
  end
end
