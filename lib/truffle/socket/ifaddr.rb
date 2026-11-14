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
  class Ifaddr
    attr_reader :addr, :broadaddr, :dstaddr, :flags, :name, :netmask

    def initialize(addr: nil, broadaddr: nil, dstaddr: nil, flags: nil, name: nil, netmask: nil)
      @addr      = addr
      @broadaddr = broadaddr
      @dstaddr   = dstaddr
      @flags     = flags
      @name      = name
      @netmask   = netmask
    end

    def ifindex
      index = Truffle::Socket::Foreign.if_nametoindex(@name)
      raise ArgumentError, "invalid interface name: #{@name}" if index == 0
      index
    end

    def inspect
      out = "#<Socket::Ifaddr #{name}"

      if addr
        out << " #{addr.inspect_sockaddr}"
      end

      if netmask
        out << " netmask=#{netmask.inspect_sockaddr}"
      end

      out + '>'
    end
  end
end
