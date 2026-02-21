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

require 'timeout'

module Truffle
  module Socket
    module Foreign
      extend ::FFI::Library
      ffi_lib ::FFI::Library::CURRENT_PROCESS

      SIZEOF_INT = ::FFI.type_size(:int)

      attach_function :_bind, :bind, [:int, :pointer, :socklen_t], :int
      attach_function :_connect, :connect, [:int, :pointer, :socklen_t], :int, blocking: true

      attach_function :accept, [:int, :pointer, :pointer], :int, blocking: true
      attach_function :close, [:int], :int
      attach_function :shutdown, [:int, :int], :int
      attach_function :listen, [:int, :int], :int
      attach_function :socket, [:int, :int, :int], :int
      attach_function :send, [:int, :pointer, :size_t, :int], :ssize_t, blocking: true

      attach_function :sendto,
        [:int, :pointer, :size_t, :int, :pointer, :socklen_t], :ssize_t, blocking: true

      attach_function :recv, [:int, :pointer, :size_t, :int], :ssize_t, blocking: true
      attach_function :recvmsg, :recvmsg, [:int, :pointer, :int], :ssize_t, blocking: true
      attach_function :sendmsg, :sendmsg, [:int, :pointer, :int], :ssize_t, blocking: true

      attach_function :recvfrom,
        [:int, :pointer, :size_t, :int, :pointer, :pointer], :int, blocking: true

      attach_function :_getsockopt,
        :getsockopt, [:int, :int, :int, :pointer, :pointer], :int

      attach_function :_getaddrinfo,
        :getaddrinfo, [:string, :string, :pointer, :pointer], :int

      attach_function :gai_strerror, [:int], :string

      attach_function :setsockopt,
        [:int, :int, :int, :pointer, :socklen_t], :int

      attach_function :freeaddrinfo, [:pointer], :void

      attach_function :_getpeername,
        :getpeername, [:int, :pointer, :pointer], :int

      attach_function :_getsockname,
        :getsockname, [:int, :pointer, :pointer], :int

      attach_function :_socketpair,
        :socketpair, [:int, :int, :int, :pointer], :int

      attach_function :gethostname, [:pointer, :size_t], :int
      attach_function :getservbyname, [:string, :string], :pointer
      attach_function :getservbyport, [:int, :string], :pointer
      attach_function :gethostbyname, [:string], :pointer
      attach_function :gethostbyaddr, [:pointer, :socklen_t, :int], :pointer

      attach_function :htons, [:uint16], :uint16
      attach_function :ntohs, [:uint16], :uint16

      attach_function :inet_network, [:string], :uint32
      attach_function :inet_pton, [:int, :string, :pointer], :int

      attach_function :_getnameinfo,
        :getnameinfo,
        [:pointer, :socklen_t, :pointer, :socklen_t, :pointer, :socklen_t, :int],
        :int

      attach_function :getifaddrs, [:pointer], :int, blocking: true
      attach_function :freeifaddrs, [:pointer], :void

      def self.bind(descriptor, sockaddr)
        sockaddr_p = Primitive.io_thread_buffer_allocate(sockaddr.bytesize)
        begin
          sockaddr_p.write_bytes(sockaddr)
          _bind(descriptor, sockaddr_p, sockaddr.bytesize)
        ensure
          Primitive.io_thread_buffer_free(sockaddr_p)
        end
      end

      def self.connect(descriptor, sockaddr, connect_timeout = nil)
        sockaddr = Socket.coerce_to_string(sockaddr)

        sockaddr_p = Primitive.io_thread_buffer_allocate(sockaddr.bytesize)
        begin
          sockaddr_p.write_bytes(sockaddr)

          if Primitive.nil?(connect_timeout)
            _connect(descriptor, sockaddr_p, sockaddr.bytesize)
          else
            # In CRuby a connect_timeout of 0 fires immediately whereas a 0 for
            # Timeout.timeout means "no timeout", so we change 0 to 1ns
            # (the smallest time interval for clock_gettime).
            Timeout.timeout(connect_timeout == 0 ? 0.000000001 : connect_timeout, IO::TimeoutError) do
              _connect(descriptor, sockaddr_p, sockaddr.bytesize)
            end
          end
        ensure
          Primitive.io_thread_buffer_free(sockaddr_p)
        end
      end

      def self.getsockopt(descriptor, level, optname)
        buffer, val, length = Truffle::FFI::Pool.stack_alloc(256, Primitive.pointer_find_type_size(:socklen_t))
        begin
          length.write_int(256)

          err = _getsockopt(descriptor, level, optname, val, length)

          Errno.handle('Unable to get socket option') unless err == 0

          val.read_string(length.read_int)
        ensure
          Truffle::FFI::Pool.stack_free(buffer)
        end
      end

      def self.getaddrinfo(host, service = nil, family = nil, socktype = nil,
                           protocol = nil, flags = nil)
        hints = Addrinfo.new

        hints[:ai_family]   = family || 0
        hints[:ai_socktype] = socktype || 0
        hints[:ai_protocol] = protocol || 0
        hints[:ai_flags]    = flags || 0

        res_p = Primitive.io_thread_buffer_allocate(Primitive.pointer_find_type_size(:pointer))

        res_p.clear
        err = _getaddrinfo(host, service, hints.pointer, res_p)

        raise resolution_error(err) unless err == 0

        ptr = res_p.read_pointer

        return [] if ptr.null?

        res = Addrinfo.new(ptr)

        addrinfos = []

        loop do
          addrinfo = []
          addrinfo << res[:ai_flags]
          addrinfo << res[:ai_family]
          addrinfo << res[:ai_socktype]
          addrinfo << res[:ai_protocol]
          addrinfo << res[:ai_addr].read_string(res[:ai_addrlen])
          addrinfo << res[:ai_canonname]

          addrinfos << addrinfo

          break if res[:ai_next].null?

          res = Addrinfo.new(res[:ai_next])
        end

        addrinfos
      ensure
        hints.pointer.free if hints

        if res_p
          ptr = res_p.read_pointer

          # Be sure to feed a legit pointer to freeaddrinfo
          freeaddrinfo(ptr) unless ptr.null?
          Primitive.io_thread_buffer_free(res_p)
        end
      end

      def self.resolution_error(code)
        error = gai_strerror(code)
        ::Socket::ResolutionError.new("getaddrinfo: #{error}", code)
      end

      def self.getaddress(host)
        addrinfos = getaddrinfo(host)

        unpack_sockaddr_in(addrinfos.first[4], false).first
      end

      def self.getnameinfo(sockaddr, flags = ::Socket::NI_NUMERICHOST | ::Socket::NI_NUMERICSERV,
                           reverse_lookup = !BasicSocket.do_not_reverse_lookup)
        name_info = []

        buffer, sockaddr_p, node, service = Truffle::FFI::Pool.stack_alloc(
          sockaddr.bytesize, ::Socket::NI_MAXHOST, ::Socket::NI_MAXSERV)
        begin
          sockaddr_p.write_bytes(sockaddr)

          if reverse_lookup
            err = _getnameinfo(sockaddr_p, sockaddr.bytesize, node,
                               ::Socket::NI_MAXHOST, nil, 0, 0)

            name_info[2] = node.read_string if err == 0
          end

          err = _getnameinfo(sockaddr_p, sockaddr.bytesize, node,
                             ::Socket::NI_MAXHOST, service,
                             ::Socket::NI_MAXSERV, flags)

          raise resolution_error(err) unless err == 0

          sa_family = SockaddrIn.new(sockaddr_p)[:sin_family]

          name_info[0] = ::Socket::Constants::AF_TO_FAMILY[sa_family]
          name_info[1] = service.read_string
          name_info[3] = node.read_string

          name_info[2] = name_info[3] unless name_info[2]

          name_info
        ensure
          Truffle::FFI::Pool.stack_free(buffer)
        end
      end

      def self.getpeername(socket)
        buffer, sockaddr_storage_p, len_p = Truffle::FFI::Pool.stack_alloc(128, Primitive.pointer_find_type_size(:socklen_t))
        begin
          len_p.write_int(128)
          err = _getpeername(socket.fileno, sockaddr_storage_p, len_p)

          Errno.handle('getpeername(2)') unless err == 0

          sockaddr_storage_p.read_string(len_p.read_int)
        ensure
          Truffle::FFI::Pool.stack_free(buffer)
        end
      end

      def self.getsockname(socket)
        buffer, sockaddr_storage_p, len_p = Truffle::FFI::Pool.stack_alloc(128, Primitive.pointer_find_type_size(:socklen_t))
        begin
          len_p.write_int(128)
          err = _getsockname(socket.fileno, sockaddr_storage_p, len_p)

          Errno.handle('getsockname(2)') unless err == 0

          sockaddr_storage_p.read_string(len_p.read_int)
        ensure
          Truffle::FFI::Pool.stack_free(buffer)
        end
      end

      def self.pack_sockaddr_in(host, port, family = ::Socket::AF_UNSPEC,
                                type = 0, flags = 0)
        if type == 0
          begin
            # Check if the port is a numeric value we must set the socket type in order for the getaddrinfo call
            # to succeed on some platforms (most notably, Solaris).
            Integer(port)
            type = ::Socket::SOCK_DGRAM
          rescue ArgumentError, TypeError
            # Ignored.
          end
        end

        if Primitive.nil?(port)
          port = 0
        end

        hints = Addrinfo.new

        hints[:ai_family]   = family
        hints[:ai_socktype] = type
        hints[:ai_flags]    = flags

        if host == ::Socket::INADDR_ANY or (host and host.empty?)
          host = '0.0.0.0'
        end

        res_p = Primitive.io_thread_buffer_allocate(Primitive.pointer_find_type_size(:pointer))
        res_p.clear

        err = _getaddrinfo(host, port.to_s, hints.pointer, res_p)

        raise resolution_error(err) unless err == 0

        return [] if res_p.read_pointer.null?

        res = Addrinfo.new(res_p.read_pointer)

        res[:ai_addr].read_string(res[:ai_addrlen])
      ensure
        hints.pointer.free if hints

        if res_p
          ptr = res_p.read_pointer

          freeaddrinfo(ptr) unless ptr.null?
          Primitive.io_thread_buffer_free(res_p)
        end
      end

      def self.unpack_sockaddr_in(sockaddr, reverse_lookup)
        family, port, host, ip = getnameinfo(
          sockaddr,
          ::Socket::NI_NUMERICHOST | ::Socket::NI_NUMERICSERV,
          reverse_lookup
        )

        # On some systems this doesn't fail for families other than AF_INET(6)
        # so we raise manually here.
        unless family.include?('AF_INET')
          raise ArgumentError, 'not an AF_INET/AF_INET6 sockaddr'
        end

        [host, ip, port.to_i]
      end

      def self.getpeereid(*)
        raise NotImplementedError,
          'getpeereid() is not supported on this platform'
      end

      def self.socketpair(family, type, protocol)
        pointer = Primitive.io_thread_buffer_allocate(Primitive.pointer_find_type_size(:int) * 2)
        begin
          pointer.clear
          status = _socketpair(family, type, protocol, pointer)

          Errno.handle('socketpair(2)') unless status == 0

          pointer.read_array_of_int(2)
        ensure
          Primitive.io_thread_buffer_free(pointer)
        end
      end

      def self.char_pointer(length, &block)
        memory_pointer(:char, length, &block)
      end

      def self.memory_pointer(*args, &block)
        ::FFI::MemoryPointer.new(*args, &block)
      end

      def self.pointers_of_type(current, type)
        pointers = []
        size     = ::FFI.type_size(type)
        pointer  = current.read_pointer

        until pointer.null?
          pointers << pointer

          current = current + size
          pointer = current.read_pointer
        end

        pointers
      end

      def self.ip_to_bytes(family, address)
        size = 16

        # Truffle: handle ip_address like "fe80::e93d:a67f:89bc:d21%wlp2s0"
        # which contains the interface name after the %
        if i = address.rindex('%')
          address = address[0...i]
        end

        pointer = Primitive.io_thread_buffer_allocate(Primitive.pointer_find_type_size(:pointer) * size)

        begin
          status = inet_pton(family, address, pointer)

          Errno.handle('inet_pton()') if status < 1

          pointer.get_array_of_uchar(0, size)
        ensure
          Primitive.io_thread_buffer_free(pointer)
        end
      end
    end
  end
end
