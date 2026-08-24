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

class UNIXSocket < BasicSocket
  def self.socketpair(type = Socket::SOCK_STREAM, protocol = 0)
    family = Socket::AF_UNIX
    type   = Truffle::Socket.socket_type(type)

    fd0, fd1 = Truffle::Socket::Foreign.socketpair(family, type, protocol)

    [for_fd(fd0), for_fd(fd1)]
  end

  class << self
    alias_method :pair, :socketpair
  end

  def initialize(path)
    @no_reverse_lookup = Primitive.class(self).do_not_reverse_lookup
    @path              = '' # empty for client sockets

    fd = Truffle::Socket::Foreign.socket(Socket::AF_UNIX, Socket::SOCK_STREAM, 0)

    Errno.handle_ffi('socket(2)') if fd < 0

    setup(fd, 'r+', true)
    binmode

    sockaddr = Socket.sockaddr_un(Truffle::Type.check_null_safe(path))
    status   = Truffle::Socket::Foreign.connect(Primitive.io_fd(self), sockaddr)

    Errno.handle_ffi('connect(2)') if status < 0
  end

  def recvfrom(bytes_read, flags = 0, buffer = nil)
    Truffle::Socket::Foreign.memory_pointer(bytes_read) do |buf|
      n_bytes = Truffle::Socket::Foreign.recvfrom(Primitive.io_fd(self), buf, bytes_read, flags, nil, nil)
      Errno.handle_ffi('recvfrom(2)') if n_bytes == -1

      message = buf.read_string(n_bytes)

      if buffer
        message = buffer.replace message.force_encoding(buffer.encoding)
      end

      return [message, ['AF_UNIX', '']]
    end
  end

  def path
    @path ||= Truffle::Socket::Foreign.getsockname(self).unpack('SZ*')[1]
  end

  def addr
    ['AF_UNIX', path]
  end

  def peeraddr
    path = Truffle::Socket::Foreign.getpeername(self).unpack('SZ*')[1]

    ['AF_UNIX', path]
  end

  def send_io(io)
    raise NotImplementedError, 'IO#send_io not yet implemented'
  end

  def recv_io(klass = IO, mode = nil)
    raise NotImplementedError, 'IO#recv_io not yet implemented'
  end
end
