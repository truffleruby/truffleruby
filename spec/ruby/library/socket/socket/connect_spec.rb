require_relative '../spec_helper'
require_relative '../fixtures/classes'

describe 'Socket#connect' do
  SocketSpecs.each_ip_protocol do |family, ip_address|
    before do
      @server = Socket.new(family, :STREAM)
      @client = Socket.new(family, :STREAM)

      @server.bind(Socket.sockaddr_in(0, ip_address))
    end

    after do
      @client.close
      @server.close
    end

    it 'returns 0 when connected successfully using a String' do
      @server.listen(1)

      @client.connect(@server.getsockname).should == 0
    end

    it 'returns 0 when connected successfully using an Addrinfo' do
      @server.listen(1)

      @client.connect(@server.connect_address).should == 0
    end

    it 'raises Errno::EISCONN when already connected' do
      @server.listen(1)

      @client.connect(@server.getsockname).should == 0

      -> {
        @client.connect(@server.getsockname)

        # A second call needed if non-blocking sockets become default
        # XXX honestly I don't expect any real code to care about this spec
        # as it's too implementation-dependent and checking for connect()
        # errors is futile anyways because of TOCTOU
        @client.connect(@server.getsockname)
      }.should.raise(Errno::EISCONN)
    end

    it 'raises IOError when the socket is closed' do
      @server.listen(1)
      @client.close

      -> { @client.connect(@server.getsockname) }.should.raise(IOError)
    end

    it 'does not call #getsockopt to check whether the connection failed' do
      address = @server.getsockname
      @server.close

      def @client.getsockopt(*)
        raise "#getsockopt should not be called"
      end

      -> { @client.connect(address) }.should.raise(Errno::ECONNREFUSED)
    end

    it 'includes the destination in the error message' do
      address = @server.getsockname
      port = Socket.unpack_sockaddr_in(address)[0]
      destination = family == Socket::AF_INET6 ? "[#{ip_address}]:#{port}" : "#{ip_address}:#{port}"
      @server.close

      -> { @client.connect(address) }.should.raise(Errno::ECONNREFUSED, /connect\(2\) for #{Regexp.escape(destination)}\z/)
    end

    platform_is_not :darwin do
      it 'raises Errno::ECONNREFUSED or Errno::ETIMEDOUT when the connection failed' do
        begin
          @client.connect(@server.getsockname)
        rescue => e
          [Errno::ECONNREFUSED, Errno::ETIMEDOUT].include?(e.class).should == true
        end
      end
    end
  end

  ruby_version_is "3.4" do
    it "fails with timeout" do
      # TEST-NET-1 IP address are reserved for documentation and example purposes.
      address = Socket.pack_sockaddr_in(1, "192.0.2.1")

      client = Socket.new(Socket::AF_INET, Socket::SOCK_STREAM)
      client.timeout = 0

      -> {
        begin
          client.connect(address)
        rescue Errno::ECONNREFUSED
          skip "Outgoing packets may be filtered"
        rescue Errno::ENETUNREACH
          skip "Off line"
        end
      }.should.raise(IO::TimeoutError)
    ensure
      client.close
    end
  end
end
