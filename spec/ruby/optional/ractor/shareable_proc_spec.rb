require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor.shareable_proc" do
    it "returns a shareable non-lambda Proc" do
      pr = Object.instance_exec { Ractor.shareable_proc { :ok } }
      pr.should.is_a?(Proc)
      pr.lambda?.should == false
      Ractor.shareable?(pr).should == true
      pr.call.should == :ok
    end

    it "returns a Proc which can be called from another Ractor" do
      pr = Object.instance_exec { Ractor.shareable_proc { |x| x * 2 } }
      Ractor.new(pr) { |pr| pr.call(21) }.value.should == 42
    end

    it "sets self to nil in the block by default" do
      Ractor.shareable_proc { self }.call.should == nil
    end

    it "sets self to the object given as self: in the block" do
      Ractor.shareable_proc(self: :sym) { self }.call.should == :sym
      Ractor.shareable_proc(self: 42) { |a, b| [self, a, b] }.call(1, 2).should == [42, 1, 2]
    end

    it "returns a Proc with the arity and parameters of the block" do
      pr = Ractor.shareable_proc(self: nil) { |a, b| }
      pr.arity.should == 2
      pr.parameters.should == [[:opt, :a], [:opt, :b]]
    end

    it "returns a Proc which passes the block it is called with to the block" do
      pr = Ractor.shareable_proc(self: nil) { |a, &block| block.call(a) + 1 }
      pr.call(4) { |x| x * 10 }.should == 41
    end

    it "raises ArgumentError if no block is given" do
      -> { Ractor.shareable_proc }.should.raise(ArgumentError, "tried to create Proc object without a block")
    end
  end
end
