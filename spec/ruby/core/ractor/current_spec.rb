require_relative '../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor.current" do
    it "returns the main Ractor in the main Ractor" do
      Ractor.current.should.equal?(Ractor.main)
    end

    it "returns the Ractor running the current code" do
      r = Ractor.new { Ractor.current }
      r.value.should.equal?(r)
    end

    it "returns the Ractor in a Thread created by that Ractor" do
      r = Ractor.new { Thread.new { Ractor.current }.value }
      r.value.should.equal?(r)
    end

    it "returns the main Ractor in a Thread created by the main Ractor" do
      Thread.new { Ractor.current }.value.should.equal?(Ractor.main)
    end
  end
end
