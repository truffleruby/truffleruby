require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor.shareable_lambda" do
    it "returns a shareable lambda" do
      pr = Object.instance_exec { Ractor.shareable_lambda { :ok } }
      pr.should.is_a?(Proc)
      pr.lambda?.should == true
      Ractor.shareable?(pr).should == true
      pr.call.should == :ok
    end

    it "returns a lambda which can be called from another Ractor" do
      pr = Object.instance_exec { Ractor.shareable_lambda { |x| x * 2 } }
      Ractor.new(pr) { |pr| pr.call(21) }.value.should == 42
    end

    it "raises ArgumentError if no block is given" do
      -> { Ractor.shareable_lambda }.should.raise(ArgumentError, "tried to create Proc object without a block")
    end
  end
end
