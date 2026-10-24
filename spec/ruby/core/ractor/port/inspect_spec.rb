require_relative '../../../spec_helper'

ruby_version_is "4.0" do
  describe "Ractor::Port#inspect" do
    it "returns a String starting with #<Ractor::Port" do
      Ractor::Port.new.inspect.should.start_with?("#<Ractor::Port")
    end
  end
end
