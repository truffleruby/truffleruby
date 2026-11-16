require_relative '../spec_helper'
require_relative '../fixtures/classes'

describe 'UNIXServer#initialize' do
  before do
    @path = SocketSpecs.socket_path
    @server = UNIXServer.new(@path)
  end

  after do
    @server.close if @server
    rm_r @path
  end

  it 'returns a new UNIXServer' do
    @server.should.instance_of?(UNIXServer)
  end

  it 'sets the socket to binmode' do
    @server.binmode?.should == true
  end

  it 'raises Errno::EADDRINUSE when the socket is already in use' do
    -> { UNIXServer.new(@path) }.should.raise(Errno::EADDRINUSE)
  end

  it 'accepts an object responding to #to_path' do
    @server.close
    rm_r @path

    path = @path
    object = Object.new
    object.define_singleton_method(:to_path) { path }

    @server = UNIXServer.new(object)
    @server.should.instance_of?(UNIXServer)
  end

  platform_is_not :windows do
    it 'preserves the bytes of a path with a non-UTF-8 encoding' do
      @server.close
      rm_r @path
      # The bytes form a valid UTF-8 filename even on filesystems requiring UTF-8,
      # but the String's encoding must not cause them to be transcoded.
      @path += "\u00E9"
      path = @path.dup.force_encoding(Encoding::ISO_8859_1)

      begin
        @server = UNIXServer.new(path)
        @server.path.b.should == path.b
        File.socket?(@path).should == true
      ensure
        rm_r path.encode(Encoding::UTF_8)
      end
    end
  end
end
