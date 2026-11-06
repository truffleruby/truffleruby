require_relative '../../../spec_helper'

describe "IO::Buffer#copy" do
  before :each do
    @buffer = IO::Buffer.new(8)
  end

  after :each do
    @buffer&.free
    @buffer = nil
  end

  it "copies the source buffer into the buffer and returns the number of bytes copied" do
    @buffer.copy(IO::Buffer.for("xyz")).should == 3
    @buffer.get_string(0, 3).should == "xyz"
  end
end
