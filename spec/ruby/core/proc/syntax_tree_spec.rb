require_relative '../../spec_helper'

ruby_version_is "4.1" do
  describe "Proc#syntax_tree" do
    def return_block(&b)
      b
    end

    it "currently returns a CallNode and not a BlockNode for a block" do
      node = proc { 42 }.syntax_tree
      node.start_line.should == __LINE__ - 1
      node.should.is_a?(Prism::CallNode)

      node = lambda { 42 }.syntax_tree
      node.start_line.should == __LINE__ - 1
      node.should.is_a?(Prism::CallNode)

      node = return_block { 42 }.syntax_tree
      node.start_line.should == __LINE__ - 1
      node.should.is_a?(Prism::CallNode)
    end

    it "returns a LambdaNode for a stabby lambda" do
      node = -> { 42 }.syntax_tree
      node.start_line.should == __LINE__ - 1
      node.should.is_a?(Prism::LambdaNode)
    end

    it "returns a ForNode for a for-loop block" do
      iter = Object.new
      def iter.each(&block)
        block.call(block)
      end
      for b in iter
        node = b.syntax_tree
      end

      node.start_line.should == __LINE__ - 4
      node.should.is_a?(Prism::ForNode)
    end
  end
end
