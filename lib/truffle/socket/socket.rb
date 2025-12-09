# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2013, Brian Shirai
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
# 3. Neither the name of the library nor the names of its contributors may be
#    used to endorse or promote products derived from this software without
#    specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY DIRECT,
# INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
# BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
# OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
# NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
# EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

class Socket < BasicSocket
  def self.ip_address_list
    ips = []

    getifaddrs.each do |ifaddr|
      ips << ifaddr.addr if ifaddr.addr
    end

    ips
  end

  def self.getaddrinfo(host, service, family = 0, socktype = 0,
                       protocol = 0, flags = 0, reverse_lookup = nil)
    if host
      host = Truffle::Socket.coerce_to_string(host)
    end

    if host && (host.empty? || host == '<any>')
      host = '0.0.0.0'
    elsif host == '<broadcast>'
      host = '255.255.255.255'
    end

    if Primitive.is_a?(service, Integer)
      service = service.to_s
    elsif service
      service = Truffle::Socket.coerce_to_string(service)
    end

    family    = Truffle::Socket.address_family(family)
    socktype  = Truffle::Socket.socket_type(socktype)
    addrinfos = Truffle::Socket::Foreign
      .getaddrinfo(host, service, family, socktype, protocol, flags)

    reverse_lookup = Truffle::Socket
      .convert_reverse_lookup(nil, reverse_lookup)

    addrinfos.map do |ai|
      addrinfo = []

      unpacked = Truffle::Socket::Foreign
        .unpack_sockaddr_in(ai[4], reverse_lookup)

      addrinfo << Socket::Constants::AF_TO_FAMILY[ai[1]]
      addrinfo << unpacked.pop # port

      # TODO BJF 30-Apr-2017 Why is this broken?
      # Canonical host is present (e.g. when AI_CANONNAME was used)
      # if ai[5] and !reverse_lookup
      #   unpacked[0] = ai[5]
      # end

      addrinfo.concat(unpacked) # hosts

      addrinfo << ai[1] # family
      addrinfo << ai[2] # socktype
      addrinfo << ai[3] # protocol

      addrinfo
    end
  end

  def self.getnameinfo(sockaddr, flags = 0)
    port   = nil
    host   = nil
    family = Socket::AF_UNSPEC

    if Primitive.is_a?(sockaddr, Array)
      if sockaddr.size == 3
        af, port, host = sockaddr
      elsif sockaddr.size == 4
        af   = sockaddr[0]
        port = sockaddr[1]
        host = sockaddr[3] || sockaddr[2]
      else
        raise ArgumentError,
          "array size should be 3 or 4, #{sockaddr.size} given"
      end

      if af == 'AF_INET'
        family = Socket::AF_INET
      elsif af == 'AF_INET6'
        family = Socket::AF_INET6
      end

      sockaddr = Truffle::Socket::Foreign
        .pack_sockaddr_in(host, port, family, Socket::SOCK_STREAM, 0)
    end

    _, port, host, _ = Truffle::Socket::Foreign.getnameinfo(sockaddr, flags)

    [host, port]
  end

  def self.gethostname
    Truffle::Socket::Foreign.char_pointer(::Socket::NI_MAXHOST) do |pointer|
      Truffle::Socket::Foreign.gethostname(pointer, pointer.total)

      pointer.read_string
    end
  end

  def self.gethostbyname(hostname)
    addrinfos = Socket
      .getaddrinfo(hostname, nil, nil, :STREAM, nil, Socket::AI_CANONNAME)

    hostname     = addrinfos[0][2]
    family       = addrinfos[0][4]
    addresses    = []
    alternatives = Truffle::Socket.aliases_for_hostname(hostname)

    addrinfos.each do |a|
      sockaddr = Socket.sockaddr_in(0, a[3])

      # Done manually because we want to read the full address as a String, even if it contains nul bytes
      if a[4] == AF_INET
        offset = Truffle::Config['platform.sockaddr_in.sin_addr.offset']
        size = Truffle::Config['platform.sockaddr_in.sin_addr.size']
        addresses << sockaddr.byteslice(offset, size)
      elsif a[4] == AF_INET6
        offset = Truffle::Config['platform.sockaddr_in6.sin6_addr.offset']
        size = Truffle::Config['platform.sockaddr_in6.sin6_addr.size']
        addresses << sockaddr.byteslice(offset, size)
      end
    end

    [hostname, alternatives, family, *addresses]
  end

  def self.gethostbyaddr(addr, family = nil)
    if !family and addr.bytesize == 16
      family = Socket::AF_INET6
    elsif !family
      family = Socket::AF_INET
    end

    family = Truffle::Socket.address_family(family)

    Truffle::Socket::Foreign.char_pointer(addr.bytesize) do |in_pointer|
      in_pointer.write_bytes(addr)

      out_pointer = Truffle::Socket::Foreign
        .gethostbyaddr(in_pointer, in_pointer.total, family)

      if out_pointer.null?
        raise SocketError, "No host found for address #{addr.inspect}"
      end

      struct = Truffle::Socket::Foreign::Hostent.new(out_pointer)

      [struct.hostname, struct.aliases, struct.type, *struct.addresses]
    end
  end

  def self.getservbyname(service, proto = 'tcp')
    pointer = Truffle::Socket::Foreign.getservbyname(service, proto)

    raise SocketError, "no such service #{service}/#{proto}" if pointer.null?

    struct = Truffle::Socket::Foreign::Servent.new(pointer)

    Truffle::Socket::Foreign.ntohs(struct.port)
  end

  def self.getservbyport(port, proto = nil)
    proto ||= 'tcp'
    pointer = Truffle::Socket::Foreign.getservbyport(port, proto)

    raise SocketError, "no such service for port #{port}/#{proto}" if pointer.null?

    struct = Truffle::Socket::Foreign::Servent.new(pointer)

    struct.name
  end

  def self.getifaddrs
    Truffle::Socket::Foreign.memory_pointer(:pointer) do |ptr|
      status = Truffle::Socket::Foreign.getifaddrs(ptr)
      Errno.handle('getifaddrs()') if status < 0

      initial = Truffle::Socket::Foreign::Ifaddrs.new(ptr.read_pointer)
      ifaddrs = []
      index   = 1

      begin
        initial.each_address do |ifaddrs_struct|
          ifaddrs << Ifaddr.new(
            addr:      ifaddrs_struct.address_to_addrinfo,
            broadaddr: ifaddrs_struct.broadcast_to_addrinfo,
            dstaddr:   ifaddrs_struct.destination_to_addrinfo,
            netmask:   ifaddrs_struct.netmask_to_addrinfo,
            name:      ifaddrs_struct.name,
            flags:     ifaddrs_struct.flags,
            ifindex:   index
          )

          index += 1
        end

        ifaddrs
      ensure
        Truffle::Socket::Foreign.freeifaddrs(initial.pointer)
      end
    end
  end

  def self.pack_sockaddr_in(port, host)
    Truffle::Socket::Foreign.pack_sockaddr_in(host, port)
  end

  def self.unpack_sockaddr_in(addr)
    addr = addr.to_sockaddr if Primitive.is_a?(addr, Addrinfo)
    _, address, port = Truffle::Socket::Foreign
      .unpack_sockaddr_in(addr, false)

    [port, address]
  rescue SocketError => e
    if e.message =~ /ai_family not supported/
      raise ArgumentError, 'not an AF_INET/AF_INET6 sockaddr'
    else
      raise e
    end
  end

  def self.socketpair(family, type, protocol = 0)
    family = Truffle::Socket.address_family(family)
    type   = Truffle::Socket.socket_type(type)

    fd1, fd2 = Truffle::Socket::Foreign.socketpair(family, type, protocol)

    s1 = for_fd(fd1)
    s2 = for_fd(fd2)

    [s1, s2].map do |sock|
      sock.instance_variable_set(:@family, family)
      sock
    end
  end

  class << self
    alias_method :sockaddr_in, :pack_sockaddr_in
    alias_method :pair, :socketpair
  end

  if Truffle::Socket.unix_socket_support?
    def self.pack_sockaddr_un(file)
      max_path_size =  Truffle::Config['platform.sockaddr_un.sun_path.size'] - 1
      if file.bytesize > max_path_size
        raise ArgumentError, "too long unix socket path (#{file.bytesize} bytes given but #{max_path_size} bytes max)"
      end

      struct = Truffle::Socket::Foreign::SockaddrUn.new
      struct[:sun_family] = Socket::AF_UNIX
      struct[:sun_path] = file

      begin
        struct.to_s
      ensure
        struct.pointer.free
      end
    end

    def self.unpack_sockaddr_un(addr)
      addr = addr.to_sockaddr if Primitive.is_a?(addr, Addrinfo)
      struct = Truffle::Socket::Foreign::SockaddrUn.with_sockaddr(addr)
      begin
        unless struct.family == Socket::AF_UNIX
          raise ArgumentError, 'not an AF_UNIX sockaddr'
        end
        struct[:sun_path].to_s
      ensure
        struct.pointer.free
      end
    end

    class << self
      alias_method :sockaddr_un, :pack_sockaddr_un

      attr_accessor :tcp_fast_fallback
    end

    @tcp_fast_fallback = ENV['RUBY_TCP_NO_FAST_FALLBACK'] == '0'
  end

  def initialize(family, socket_type, protocol = 0)
    @no_reverse_lookup = Primitive.class(self).do_not_reverse_lookup

    @family = Truffle::Socket.protocol_family(family)
    socket_type = Truffle::Socket.socket_type(socket_type)

    descriptor = Truffle::Socket::Foreign.socket(@family, socket_type, protocol)

    Errno.handle('socket(2)') if descriptor < 0

    setup(descriptor, nil, true)
    binmode
  end

  def bind(addr)
    if Primitive.is_a?(addr, Addrinfo)
      addr = addr.to_sockaddr
    end

    err = Truffle::Socket::Foreign.bind(Primitive.io_fd(self), addr)

    Errno.handle('bind(2)') unless err == 0

    0
  end

  def connect(sockaddr)
    if Primitive.is_a?(sockaddr, Addrinfo)
      sockaddr = sockaddr.to_sockaddr
    end

    status = Truffle::Socket::Foreign.connect(Primitive.io_fd(self), sockaddr)

    Truffle::Socket::Error.connect_error('connect(2)', self) if status < 0

    0
  end

  private def __connect_nonblock(sockaddr, exception)
    self.nonblock = true

    if Primitive.is_a?(sockaddr, Addrinfo)
      sockaddr = sockaddr.to_sockaddr
    end

    status = Truffle::Socket::Foreign.connect(Primitive.io_fd(self), sockaddr)

    if status < 0
      if exception
        Truffle::Socket::Error.connect_nonblock('connect(2)')
      else
        errno = Errno.errno
        if errno == Errno::EINPROGRESS::Errno
          :wait_writable
        elsif errno == Errno::EISCONN::Errno
          0
        else
          Truffle::Socket::Error.connect_nonblock('connect(2)')
        end
      end
    else
      0
    end
  end

  def recvfrom(bytes, flags = 0)
    message, addr = recvmsg(bytes, flags)
    return nil if Primitive.nil?(message)

    [message, addr]
  end

  private def __recvfrom_nonblock(bytes, flags, buffer, exception)
    message, addr = recvmsg_nonblock(bytes, flags, exception: exception)
    return nil if Primitive.nil?(message)

    if message == :wait_readable
      message
    else
      if buffer
        message = buffer.replace message.force_encoding(buffer.encoding)
      end
      [message, addr]
    end
  end

  def listen(backlog)
    Truffle::Socket.listen(self, backlog)
  end

  def accept
    Truffle::Socket.accept_and_addrinfo(self, Socket, true)
  end

  private def __accept_nonblock(exception)
    self.nonblock = true
    Truffle::Socket.accept_and_addrinfo(self, Socket, exception)
  end

  def sysaccept
    socket, addrinfo = accept

    [socket.fileno, addrinfo]
  end
end
