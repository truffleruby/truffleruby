require_relative '../../../spec_helper'

describe "IO::Buffer#set_string" do
  before :each do
    @buffer = IO::Buffer.new(8)
  end

  after :each do
    @buffer&.free
    @buffer = nil
  end

  it "copies the string into the buffer and returns the number of bytes copied" do
    @buffer.set_string("abcd").should == 4
    @buffer.get_string(0, 4).should == "abcd"
  end
end
