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

class TCPSocket < IPSocket
  def self.gethostbyname(hostname)
    addrinfos = Socket
      .getaddrinfo(hostname, nil, nil, :STREAM, nil, Socket::AI_CANONNAME)

    hostname     = addrinfos[0][2]
    family       = addrinfos[0][4]
    addresses    = addrinfos.map { |a| a[3] }
    alternatives = []

    Truffle::Socket.aliases_for_hostname(hostname).each do |name|
      alternatives << name unless name == hostname
    end

    [hostname, alternatives, family, *addresses]
  end

  def initialize(host, port, local_host = nil, local_port = nil, connect_timeout: nil, resolv_timeout: nil, open_timeout: nil, fast_fallback: Socket.tcp_fast_fallback)
    begin
      socket = Socket.tcp(host, port, local_host, local_port, connect_timeout:, resolv_timeout:, open_timeout:, fast_fallback:)
    rescue Errno::ETIMEDOUT
      raise IO::TimeoutError
    end
    fd = socket.fileno
    socket.autoclose = false
    socket.close

    @no_reverse_lookup = Primitive.class(self).do_not_reverse_lookup
    setup(fd, nil, true)
    binmode
  end
end
