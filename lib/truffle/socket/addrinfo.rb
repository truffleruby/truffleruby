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

class Addrinfo
  attr_reader :afamily, :pfamily, :socktype, :protocol

  attr_reader :canonname

  def self.getaddrinfo(nodename, service, family = nil, socktype = nil,
                       protocol = nil, flags = nil, timeout: nil)
    # NOTE: timeout is ignored currently. On MRI it's ignored but only for platforms without getaddrinfo_a().

    raw = Socket.getaddrinfo(nodename, service, family, socktype, protocol, flags)

    raw.map do |pair|
      lfamily, lport, lhost, laddress, _, lsocktype, lprotocol = pair

      sockaddr = Socket.pack_sockaddr_in(lport, laddress)
      addr = Addrinfo.new(sockaddr, lfamily, lsocktype, lprotocol)

      if flags && (flags | Socket::AI_CANONNAME) != 0
        addr.instance_variable_set(:@canonname, lhost)
      end

      addr
    end
  end

  def self.ip(ip)
    sockaddr = Socket.sockaddr_in(0, ip)
    family = Truffle::Socket::Foreign::Sockaddr.family_of_string(sockaddr)

    new(sockaddr, family)
  end

  def self.tcp(ip, port)
    sockaddr = Socket.sockaddr_in(port, ip)
    pfamily = Truffle::Socket::Foreign::Sockaddr.family_of_string(sockaddr)

    new(sockaddr, pfamily, Socket::SOCK_STREAM, Socket::IPPROTO_TCP)
  end

  def self.udp(ip, port)
    sockaddr = Socket.sockaddr_in(port, ip)
    pfamily = Truffle::Socket::Foreign::Sockaddr.family_of_string(sockaddr)

    new(sockaddr, pfamily, Socket::SOCK_DGRAM, Socket::IPPROTO_UDP)
  end

  def self.unix(socket, socktype = nil)
    socktype ||= Socket::SOCK_STREAM

    new(Socket.pack_sockaddr_un(socket), Socket::PF_UNIX, socktype)
  end

  # Addrinfo#initialize has a bunch of checks that prevent us from setting
  # certain address families (e.g. AF_PACKET). Meanwhile methods such as
  # Socket.getifaddrs need to create Addrinfo instances with exactly those
  # address families.
  #
  # Because modifying #initialize would break compatibility we have to define a
  # separate new-like method that completely ignores #initialize.
  #
  # For the sake of simplicity `family` **must** be an Integer, a String based
  # address family is not supported.
  def self.raw_with_family(family)
    instance = allocate

    instance.instance_variable_set(:@afamily, family)

    instance
  end

  def initialize(sockaddr, pfamily = nil, socktype = 0, protocol = 0)
    if Primitive.is_a?(sockaddr, Array)
      @afamily    = Truffle::Socket.address_family(sockaddr[0])
      @ip_port    = sockaddr[1]
      @ip_address = sockaddr[3]

      # When using AF_INET6 the protocol family can only be PF_INET6
      if @afamily == Socket::AF_INET6 and !pfamily
        pfamily = Socket::PF_INET6
      end
    else
      @afamily = Truffle::Socket::Foreign::Sockaddr.family_of_string(sockaddr)
      case @afamily
      when Socket::AF_UNIX
        @unix_path = Socket.unpack_sockaddr_un(sockaddr)
      when Socket::AF_INET
        @ip_port, @ip_address = Socket.unpack_sockaddr_in(sockaddr)
      when Socket::AF_INET6
        @ip_port, @ip_address = Socket.unpack_sockaddr_in(sockaddr)
      end
    end

    @pfamily ||= Truffle::Socket.protocol_family(pfamily)

    @socktype = Truffle::Socket.socket_type(socktype || 0)
    @protocol = protocol || 0

    # Per MRI behaviour setting the protocol family should also set the address
    # family, but only if the address and protocol families are compatible.
    if @pfamily && @pfamily != 0
      if @afamily == Socket::AF_INET6 and
      @pfamily != Socket::PF_INET and
      @pfamily != Socket::PF_INET6
        raise SocketError, 'The given protocol and address families are incompatible'
      end

      @afamily = @pfamily
    end

    # MRI only checks this if "sockaddr" is an Array.
    if Primitive.is_a?(sockaddr, Array)
      if @afamily == Socket::AF_INET6
        if Socket.sockaddr_in(0, @ip_address).bytesize != 28
          raise SocketError, "Invalid IPv6 address: #{@ip_address.inspect}"
        end
      end
    end

    # Based on MRI's (re-)implementation of getaddrinfo()
    if @afamily != Socket::AF_UNIX and
    @afamily != Socket::AF_UNSPEC and
    @afamily != Socket::AF_INET and
    @afamily != Socket::AF_INET6
      raise(
        SocketError,
        'Address family must be AF_UNIX, AF_INET, AF_INET6, PF_INET or PF_INET6'
      )
    end

    # Per MRI this validation should only happen when "sockaddr" is an Array.
    if Primitive.is_a?(sockaddr, Array)
      case @socktype
      when 0, nil
        if @protocol != 0 and !Primitive.nil?(@protocol) and @protocol != Socket::IPPROTO_UDP
          raise SocketError, 'Socket protocol must be IPPROTO_UDP or left unset'
        end
      when Socket::SOCK_RAW
        # nothing to do
      when Socket::SOCK_DGRAM
        if @protocol != Socket::IPPROTO_UDP and @protocol != 0
          raise SocketError, 'Socket protocol must be IPPROTO_UDP or left unset'
        end
      when Socket::SOCK_STREAM
        if @protocol != Socket::IPPROTO_TCP and @protocol != 0
          raise SocketError, 'Socket protocol must be IPPROTO_TCP or left unset'
        end
      # Based on MRI behaviour, though MRI itself doesn't seem to explicitly
      # handle this case (possibly handled by getaddrinfo()).
      when Socket::SOCK_SEQPACKET
        if @protocol != 0
          raise SocketError, 'SOCK_SEQPACKET can not be used with an explicit protocol'
        end
      else
        raise SocketError, 'Unsupported socket type'
      end
    end
  end

  def unix?
    @afamily == Socket::AF_UNIX
  end

  def ipv4?
    @afamily == Socket::AF_INET
  end

  def ipv6?
    @afamily == Socket::AF_INET6
  end

  def ip?
    ipv4? || ipv6?
  end

  def ip_address
    raise SocketError, 'need IPv4 or IPv6 address' unless ip?

    @ip_address
  end

  def ip_port
    raise SocketError, 'need IPv4 or IPv6 address' unless ip?

    @ip_port
  end

  def unix_path
    unless unix?
      raise SocketError, 'The address family must be AF_UNIX'
    end

    @unix_path
  end

  def to_sockaddr
    if unix?
      Socket.sockaddr_un(@unix_path)
    else
      Socket.sockaddr_in(@ip_port.to_i, @ip_address.to_s)
    end
  end

  alias_method :to_s, :to_sockaddr

  def getnameinfo(flags = 0)
    Socket.getnameinfo(to_sockaddr, flags)
  end

  def inspect_sockaddr
    if ipv4?
      if ip_port and ip_port != 0
        "#{ip_address}:#{ip_port}"
      elsif ip_address
        ip_address.dup
      else
        'UNKNOWN'
      end
    elsif ipv6?
      if ip_port and ip_port != 0
        "[#{ip_address}]:#{ip_port}"
      else
        ip_address.dup
      end
    elsif unix?
      if unix_path.start_with?(File::SEPARATOR)
        unix_path.dup
      else
        "UNIX #{unix_path}"
      end
    else
      Truffle::Socket.address_family_name(afamily)
    end
  end

  def inspect
    if socktype and socktype != 0
      if ip?
        case socktype
        when Socket::SOCK_STREAM
          suffix = 'TCP'
        when Socket::SOCK_DGRAM
          suffix = 'UDP'
        else
          suffix = Truffle::Socket.socket_type_name(socktype)
        end
      else
        suffix = Truffle::Socket.socket_type_name(socktype)
      end

      "#<Addrinfo: #{inspect_sockaddr} #{suffix}>"
    else
      "#<Addrinfo: #{inspect_sockaddr}>"
    end
  end

  def ip_unpack
    unless ip?
      raise SocketError, 'An IPv4/IPv6 address is required'
    end

    [ip_address, ip_port]
  end

  def ipv4_loopback?
    return false unless ipv4?
    Truffle::Socket::Foreign.inet_network(ip_address) & 0xff000000 == 0x7f000000
  end

  def ipv4_multicast?
    return false unless ipv4?
    Truffle::Socket::Foreign.inet_network(ip_address) & 0xf0000000 == 0xe0000000
  end

  def ipv4_private?
    return false unless ipv4?
    num = Truffle::Socket::Foreign.inet_network(ip_address)
    num & 0xff000000 == 0x0a000000 ||
      num & 0xfff00000 == 0xac100000 ||
      num & 0xffff0000 == 0xc0a80000
  end

  def ipv6_loopback?
    return false unless ipv6?
    Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address) ==
      Truffle::Socket::IPv6::LOOPBACK
  end

  def ipv6_linklocal?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    bytes[0] == 0xfe && bytes[1] >= 0x80
  end

  def ipv6_multicast?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    bytes[0] == 0xff
  end

  def ipv6_sitelocal?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    bytes[0] == 0xfe && bytes[1] >= 0xe0
  end

  def ipv6_mc_global?
    ipv6_mc_flag?(0xe)
  end

  def ipv6_mc_linklocal?
    ipv6_mc_flag?(0x2)
  end

  def ipv6_mc_nodelocal?
    ipv6_mc_flag?(0x1)
  end

  def ipv6_mc_orglocal?
    ipv6_mc_flag?(0x8)
  end

  def ipv6_mc_sitelocal?
    ipv6_mc_flag?(0x5)
  end

  def ipv6_mc_flag?(value)
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    bytes[0] == 0xff && bytes[1] & 0xf == value
  end
  private :ipv6_mc_flag?

  def ipv6_to_ipv4
    return unless ipv6?

    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)

    if Truffle::Socket::IPv6.ipv4_embedded?(bytes)
      Addrinfo.ip(bytes.last(4).join('.'))
    else
      nil
    end
  end

  def ipv6_unspecified?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    bytes == Truffle::Socket::IPv6::UNSPECIFIED
  end

  def ipv6_v4compat?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    Truffle::Socket::IPv6.ipv4_compatible?(bytes)
  end

  def ipv6_v4mapped?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    Truffle::Socket::IPv6.ipv4_mapped?(bytes)
  end

  def ipv6_unique_local?
    return false unless ipv6?
    bytes = Truffle::Socket::Foreign.ip_to_bytes(afamily, ip_address)
    bytes[0] == 0xfc || bytes[0] == 0xfd
  end

  def marshal_dump
    if unix?
      address = unix_path
    else
      address = [ip_address, ip_port.to_s]
    end

    if unix?
      protocol = 0
    else
      protocol = Truffle::Socket.protocol_name(self.protocol)
    end

    [
      Truffle::Socket.address_family_name(afamily),
      address,
      Truffle::Socket.protocol_family_name(pfamily),
      Truffle::Socket.socket_type_name(socktype),
      protocol,
      canonname
    ]
  end

  def marshal_load(array)
    afamily, address, pfamily, socktype, protocol, canonname = array

    @afamily  = Truffle::Socket.address_family(afamily)
    @pfamily  = Truffle::Socket.protocol_family(pfamily)
    @socktype = Truffle::Socket.socket_type(socktype)

    if protocol and protocol != 0
      @protocol = ::Socket.const_get(protocol)
    else
      @protocol = protocol
    end

    if unix?
      @unix_path = address
    else
      @ip_address = address[0]
      @ip_port    = address[1].to_i
      @canonname  = canonname
    end
  end
end
