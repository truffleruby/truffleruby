require_relative '../../spec_helper'

# Ractor isolation (objects being copied or moved between Ractors, Ractor::IsolationError, etc)
# is intentionally not specified here, so these specs also pass on implementations of Ractor
# on top of Threads without isolation, such as TruffleRuby.
ruby_version_is "4.0" do
  describe "Ractor.new" do
    it "returns a Ractor" do
      r = Ractor.new { }
      r.should.is_a?(Ractor)
      r.join
    end

    it "runs the block in a new Ractor" do
      r = Ractor.new { Ractor.current }
      r.value.should.equal?(r)
    end

    it "runs the block in a new Thread" do
      r = Ractor.new { Thread.current }
      r.value.should_not.equal?(Thread.current)
    end

    it "uses the Ractor as self in the block" do
      r = Ractor.new { self }
      r.value.should.equal?(r)
    end

    it "passes the arguments to the block" do
      r = Ractor.new(1, 2) { |a, b| [a, b] }
      r.value.should == [1, 2]
    end

    it "raises ArgumentError if no block is given" do
      -> { Ractor.new }.should.raise(ArgumentError, "must be called with a block")
    end

    it "accepts a name: keyword argument" do
      r = Ractor.new(name: "worker") { }
      r.name.should == "worker"
      r.join
    end

    it "raises TypeError if the name is not a String" do
      -> { Ractor.new(name: 42) { } }.should.raise(TypeError, "no implicit conversion of Integer into String")
    end

    it "raises ArgumentError if the name contains a null byte" do
      -> { Ractor.new(name: "a\0b") { } }.should.raise(ArgumentError, "string contains null byte")
    end
  end
end
