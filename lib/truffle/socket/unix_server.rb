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

class UNIXServer < UNIXSocket
  def initialize(path)
    @no_reverse_lookup = Primitive.class(self).do_not_reverse_lookup
    @path              = Truffle::Type.check_null_safe(path)

    fd = Truffle::Socket::Foreign.socket(Socket::AF_UNIX, Socket::SOCK_STREAM, 0)

    Errno.handle_ffi('socket(2)') if fd < 0

    setup(fd, 'r+', true)
    binmode

    sockaddr = Socket.sockaddr_un(@path)
    status   = Truffle::Socket::Foreign.bind(Primitive.io_fd(self), sockaddr)

    Errno.handle_ffi('bind(2)') if status < 0

    listen(Socket::SOMAXCONN)
  end

  def listen(backlog)
    Truffle::Socket.listen(self, backlog)
  end

  def accept
    Truffle::Socket.accept(self, UNIXSocket, true)
  end

  private def __accept_nonblock(exception)
    self.nonblock = true
    Truffle::Socket.accept(self, UNIXSocket, exception)
  end

  def sysaccept
    accept.fileno
  end

  def inspect
    "#{super[0...-1]} #{@path}>"
  end
end
