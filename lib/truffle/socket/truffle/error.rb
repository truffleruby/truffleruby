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

module Truffle
  module Socket
    module Error
      def self.read_error(message, socket)
        if socket.nonblock?
          read_nonblock(message)
        else
          Errno.handle_ffi(message)
        end
      end

      def self.write_error(message, socket)
        if socket.nonblock?
          write_nonblock(message)
        else
          Errno.handle_ffi(message)
        end
      end

      def self.connect_error(message, socket)
        if socket.nonblock?
          connect_nonblock(message)
        else
          Errno.handle_ffi(message)
        end
      end

      # Handles an error for a non-blocking read operation.
      def self.read_nonblock(message)
        errno = ::FFI.errno
        if errno == Errno::EAGAIN::Errno
          raise ::IO::EAGAINWaitReadable, message
        else
          Errno.handle_errno(errno, message)
        end
      end

      # Handles an error for a non-blocking write operation.
      def self.write_nonblock(message)
        errno = ::FFI.errno
        if errno == Errno::EAGAIN::Errno
          raise ::IO::EAGAINWaitWritable, message
        else
          Errno.handle_errno(errno, message)
        end
      end

      # Handles an error for a non-blocking connect operation.
      def self.connect_nonblock(message)
        errno = ::FFI.errno
        if errno == Errno::EAGAIN::Errno
          raise ::IO::EAGAINWaitWritable, message
        elsif errno == Errno::EINPROGRESS::Errno
          raise ::IO::EINPROGRESSWaitWritable, message
        else
          Errno.handle_errno(errno, message)
        end
      end
    end
  end
end
