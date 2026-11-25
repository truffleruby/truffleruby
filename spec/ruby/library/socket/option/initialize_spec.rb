require_relative '../spec_helper'

describe 'Socket::Option#initialize' do
  before do
    @bool = [0].pack('i')
  end

  describe 'using Integers' do
    it 'returns a Socket::Option' do
      opt = Socket::Option
        .new(Socket::AF_INET, Socket::SOL_SOCKET, Socket::SO_KEEPALIVE, @bool)

      opt.should.instance_of?(Socket::Option)

      opt.family.should  == Socket::AF_INET
      opt.level.should   == Socket::SOL_SOCKET
      opt.optname.should == Socket::SO_KEEPALIVE
      opt.data.should    == @bool
    end
  end

  describe 'using Symbols' do
    it 'returns a Socket::Option' do
      opt = Socket::Option.new(:INET, :SOCKET, :KEEPALIVE, @bool)

      opt.should.instance_of?(Socket::Option)

      opt.family.should  == Socket::AF_INET
      opt.level.should   == Socket::SOL_SOCKET
      opt.optname.should == Socket::SO_KEEPALIVE
      opt.data.should    == @bool
    end

    it 'raises when using an invalid address family' do
      -> {
        Socket::Option.new(:INET2, :SOCKET, :KEEPALIVE, @bool)
      }.should.raise(SocketError)
    end

    it 'raises when using an invalid level' do
      -> {
        Socket::Option.new(:INET, :CATS, :KEEPALIVE, @bool)
      }.should.raise(SocketError)
    end

    it 'raises when using an invalid option name' do
      -> {
        Socket::Option.new(:INET, :SOCKET, :CATS, @bool)
      }.should.raise(SocketError)
    end
  end

  describe 'using Strings' do
    it 'returns a Socket::Option' do
      opt = Socket::Option.new('INET', 'SOCKET', 'KEEPALIVE', @bool)

      opt.should.instance_of?(Socket::Option)

      opt.family.should  == Socket::AF_INET
      opt.level.should   == Socket::SOL_SOCKET
      opt.optname.should == Socket::SO_KEEPALIVE
      opt.data.should    == @bool
    end

    it 'raises when using an invalid address family' do
      -> {
        Socket::Option.new('INET2', 'SOCKET', 'KEEPALIVE', @bool)
      }.should.raise(SocketError)
    end

    it 'raises when using an invalid level' do
      -> {
        Socket::Option.new('INET', 'CATS', 'KEEPALIVE', @bool)
      }.should.raise(SocketError)
    end

    it 'raises when using an invalid option name' do
      -> {
        Socket::Option.new('INET', 'SOCKET', 'CATS', @bool)
      }.should.raise(SocketError)
    end
  end

  describe 'using custom objects' do
    it 'converts the family with #to_int' do
      family = mock(:family)
      family.should_receive(:to_int).and_return(Socket::AF_INET)

      Socket::Option.new(family, :SOCKET, :KEEPALIVE, @bool).family.should == Socket::AF_INET
    end

    it 'converts the data with #to_str' do
      data = mock(:data)
      data.should_receive(:to_str).and_return(@bool)

      opt = Socket::Option.new(:INET, :SOCKET, :KEEPALIVE, data)
      opt.data.should == @bool
      opt.bool.should == false
    end
  end
end
