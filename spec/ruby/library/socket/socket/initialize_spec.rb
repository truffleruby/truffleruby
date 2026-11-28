require_relative '../spec_helper'

describe 'Socket#initialize' do
  before do
    @socket = nil
  end

  after do
    @socket.close if @socket
  end

  describe 'using an Integer as the 1st and 2nd arguments' do
    it 'returns a Socket' do
      @socket = Socket.new(Socket::AF_INET, Socket::SOCK_STREAM)

      @socket.should.instance_of?(Socket)
    end
  end

  describe 'using Symbols as the 1st and 2nd arguments' do
    it 'returns a Socket' do
      @socket = Socket.new(:INET, :STREAM)

      @socket.should.instance_of?(Socket)
    end
  end

  describe 'using Strings as the 1st and 2nd arguments' do
    it 'returns a Socket' do
      @socket = Socket.new('INET', 'STREAM')

      @socket.should.instance_of?(Socket)
    end
  end

  describe 'using objects that respond to #to_str' do
    it 'returns a Socket' do
      family = mock(:family)
      type   = mock(:type)

      family.stub!(:to_str).and_return('AF_INET')
      type.stub!(:to_str).and_return('STREAM')

      @socket = Socket.new(family, type)

      @socket.should.instance_of?(Socket)
    end

    it 'raises TypeError when the #to_str method does not return a String' do
      family = mock(:family)
      type   = mock(:type)

      family.stub!(:to_str).and_return(Socket::AF_INET)
      type.stub!(:to_str).and_return(Socket::SOCK_STREAM)

      -> { Socket.new(family, type) }.should.raise(TypeError)
    end
  end

  describe 'using objects that respond to #to_int' do
    it 'converts the family with #to_int' do
      family = mock(:family)
      family.should_receive(:to_int).and_return(Socket::AF_UNIX)

      @socket = Socket.new(family, :STREAM)

      @socket.local_address.afamily.should == Socket::AF_UNIX
    end

    it 'converts the socket type with #to_int' do
      type = mock(:type)
      type.should_receive(:to_int).and_return(Socket::SOCK_DGRAM)

      @socket = Socket.new(:INET, type)

      @socket.getsockopt(:SOCKET, :TYPE).int.should == Socket::SOCK_DGRAM
    end

    it 'converts the protocol with #to_int' do
      protocol = mock(:protocol)
      protocol.should_receive(:to_int).and_return(Socket::IPPROTO_TCP)

      @socket = Socket.new(:INET, :STREAM, protocol)

      @socket.should.instance_of?(Socket)
    end
  end

  describe 'using a custom protocol' do
    it 'returns a Socket when using an Integer' do
      @socket = Socket.new(:INET, :STREAM, Socket::IPPROTO_TCP)

      @socket.should.instance_of?(Socket)
    end

    it 'raises TypeError when using a Symbol' do
      -> { Socket.new(:INET, :STREAM, :TCP) }.should.raise(TypeError)
    end
  end

  it 'sets the do_not_reverse_lookup option' do
    @socket = Socket.new(:INET, :STREAM)

    @socket.do_not_reverse_lookup.should == Socket.do_not_reverse_lookup
  end

  it "sets basic IO accessors" do
    @socket = Socket.new(:INET, :STREAM)
    @socket.lineno.should == 0
  end

  it "sets the socket to binary mode" do
    @socket = Socket.new(:INET, :STREAM)
    @socket.binmode?.should == true
  end
end
