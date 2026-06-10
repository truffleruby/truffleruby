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

class IPSocket < BasicSocket
  undef_method :getpeereid

  def self.getaddress(host)
    Truffle::Socket::Foreign.getaddress(host)
  end

  def addr(reverse_lookup = nil)
    Truffle::Socket.address_info(:getsockname, self, reverse_lookup)
  end

  def peeraddr(reverse_lookup = nil)
    Truffle::Socket.address_info(:getpeername, self, reverse_lookup)
  end

  private def internal_recvfrom(maxlen, flags, buffer, exception)
    message, addr = internal_recvmsg(maxlen, flags, nil, false, exception)
    return message if message == :wait_readable || Primitive.nil?(message)

    aname    = Truffle::Socket.address_family_name(addr.afamily)
    hostname = addr.ip_address

    # We're re-using recvmsg which doesn't return the reverse hostname, thus
    # we'll do an extra lookup in case this is needed.
    unless do_not_reverse_lookup
      addrinfos = Socket.getaddrinfo(addr.ip_address, nil, addr.afamily,
                                     addr.socktype, addr.protocol, 0, true)

      unless addrinfos.empty?
        hostname = addrinfos[0][2]
      end
    end

    if buffer
      message = buffer.replace message.force_encoding(buffer.encoding)
    end

    [message, [aname, addr.ip_port, hostname, addr.ip_address]]
  end

  def recvfrom(maxlen, flags = 0)
    flags = 0 if Primitive.nil?(flags)
    internal_recvfrom(maxlen, flags, nil, true)
  end

  def inspect
    return "#<#{Primitive.class(self)}:(closed)>" if closed?

    family, port, _host, ip = addr
    "#<#{Primitive.class(self)}:fd #{fileno}, #{family}, #{ip}, #{port}>"
  end
end
