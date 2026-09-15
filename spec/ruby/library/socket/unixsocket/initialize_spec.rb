require_relative '../spec_helper'
require_relative '../fixtures/classes'

describe 'UNIXSocket#initialize' do
  describe 'using a non existing path' do
    platform_is_not :windows do
      it 'raises Errno::ENOENT' do
        -> { UNIXSocket.new(SocketSpecs.socket_path) }.should.raise(Errno::ENOENT)
      end
    end

    platform_is :windows do
      # Why, Windows, why?
      it 'raises Errno::ECONNREFUSED' do
        -> { UNIXSocket.new(SocketSpecs.socket_path) }.should.raise(Errno::ECONNREFUSED)
      end
    end
  end

  describe 'using an existing socket path' do
    before do
      @path = SocketSpecs.socket_path
      @server = UNIXServer.new(@path)
      @socket = UNIXSocket.new(@path)
    end

    after do
      @socket.close
      @server.close
      rm_r(@path)
    end

    it 'returns a new UNIXSocket' do
      @socket.should.instance_of?(UNIXSocket)
    end

    it 'sets the socket path to an empty String' do
      @socket.path.should == ''
    end

    it 'sets the socket to binmode' do
      @socket.binmode?.should == true
    end

    platform_is_not :windows do
      it 'sets the socket to nonblock' do
        require 'io/nonblock'
        @socket.should.nonblock?
      end
    end

    it 'sets the socket to close on exec' do
      @socket.should.close_on_exec?
    end

    it 'accepts an object responding to #to_path' do
      path = @path
      object = Object.new
      object.define_singleton_method(:to_path) { path }

      socket = UNIXSocket.new(object)
      begin
        socket.should.instance_of?(UNIXSocket)
      ensure
        socket.close
      end
    end
  end
end
