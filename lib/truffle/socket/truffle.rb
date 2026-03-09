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
    def self.bsd_support?
      Truffle::Platform.bsd? || Truffle::Platform.darwin?
    end

    def self.linux_support?
      Truffle::Platform.linux?
    end

    def self.unix_socket_support?
      ::Socket::Constants.const_defined?(:AF_UNIX)
    end

    def self.config(struct_class, base, *fields)
      struct_class.instance_variable_set(:@size, Truffle::Config["#{base}.sizeof"])
      layout_args = []

      fields.each do |field|
        field  = field.to_sym
        offset = Truffle::Config["#{base}.#{field}.offset"]
        size   = Truffle::Config["#{base}.#{field}.size"]
        type   = Truffle::Config.lookup("#{base}.#{field}.type")
        type   = if type == 'char_array'
                   [:char, size]
                 else
                   type.to_sym
                 end

        if !(Primitive.is_a?(type, Array)) && ::FFI.find_type(type).size != size
          raise ArgumentError, "FFI::Type#size of #{type} (#{::FFI.find_type(type).size}) differs from config size (#{size})"
        end

        layout_args << field << type << offset
      end

      struct_class.layout(*layout_args)
    end

    def self.aliases_for_hostname(hostname)
      pointer = Foreign.gethostbyname(hostname)
      return [] if pointer.null? # Truffle: added null check

      Foreign::Hostent.new(pointer).aliases
    end

    def self.sockaddr_class_for_socket(socket)
      if Primitive.is_a?(socket, ::UNIXSocket)
        return Foreign::SockaddrUn
      end

      # Socket created using for example Socket.unix('foo')
      if Primitive.is_a?(socket, ::Socket) and
        socket.instance_variable_get(:@family) == ::Socket::AF_UNIX
        return Foreign::SockaddrUn
      end

      case address_info(:getsockname, socket)[0]
      when 'AF_INET6'
        Foreign::SockaddrIn6
      when 'AF_UNIX'
        Foreign::SockaddrUn
      else
        Foreign::SockaddrIn
      end
    end

    def self.accept_and_addrinfo(source, new_class, exception)
      raise IOError, 'socket has been closed' if source.closed?

      sockaddr = sockaddr_class_for_socket(source).new

      begin
        fd = Truffle::Socket::Foreign.memory_pointer(:int) do |size_p|
          size_p.write_int(sockaddr.size)

          Truffle::Socket::Foreign.accept(source.fileno, sockaddr.pointer, size_p)
        end

        if fd < 0
          if !exception and Errno.errno == Truffle::POSIX::EAGAIN_ERRNO
            return :wait_readable
          else
            Error.read_error('accept(2)', source)
          end
        end

        socket = new_class.for_fd(fd)

        socktype = source.getsockopt(:SOCKET, :TYPE).int
        addrinfo = Addrinfo.new(sockaddr.to_s, sockaddr.family, socktype)

        [socket, addrinfo]
      ensure
        sockaddr.pointer.free
      end
    end

    def self.accept(source, new_class, exception)
      raise IOError, 'socket has been closed' if source.closed?

      fd = Truffle::Socket::Foreign.accept(source.fileno, ::FFI::Pointer::NULL, ::FFI::Pointer::NULL)
      if fd < 0
        if !exception and Errno.errno == Truffle::POSIX::EAGAIN_ERRNO
          return :wait_readable
        else
          Error.read_error('accept(2)', source)
        end
      end

      new_class.for_fd(fd)
    end

    def self.listen(source, backlog)
      backlog = Primitive.rb_to_int(backlog)
      err     = Foreign.listen(source.fileno, backlog)

      Error.read_error('listen(2)', source) if err < 0

      0
    end

    def self.constant_pairs
      # Truffle: no need to filter here since only defined constants are in the config
      vals = {}
      section = 'platform.socket.'
      Truffle::Config.section(section) do |key, value|
        vals[Primitive.string_substring(key, section.size, key.length)] = value
      end
      vals
    end

    def self.coerce_to_string(object)
      if Primitive.is_a?(object, String) or Primitive.is_a?(object, Symbol)
        object.to_s
      elsif object.respond_to?(:to_str)
        Primitive.convert_to_str(object)
      else
        raise TypeError, "no implicit conversion of #{object.inspect} into Integer"
      end
    end

    def self.family_prefix?(family)
      family.start_with?('AF_') || family.start_with?('PF_')
    end

    def self.prefix_with(name, prefix)
      unless name.start_with?(prefix)
        name = "#{prefix}#{name}"
      end

      name
    end

    def self.prefixed_socket_constant(name, prefix, &block)
      prefixed = prefix_with(name, prefix)

      socket_constant(prefixed, &block)
    end

    def self.socket_constant(name)
      if ::Socket.const_defined?(name)
        ::Socket.const_get(name)
      else
        raise SocketError, yield
      end
    end

    def self.address_family(family)
      case family
      when Symbol, String
        f = family.to_s

        unless family_prefix?(f)
          f = 'AF_' + f
        end

        if ::Socket.const_defined?(f)
          ::Socket.const_get(f)
        else
          raise SocketError, "unknown socket domain: #{family}"
        end
      when Integer
        family
      when NilClass
        ::Socket::AF_UNSPEC
      else
        if family.respond_to?(:to_str)
          address_family(Primitive.convert_to_str(family))
        else
          raise SocketError, "unknown socket domain: #{family}"
        end
      end
    end

    def self.address_family_name(family_int)
      # Both AF_LOCAL and AF_UNIX use value 1. CRuby seems to prefer AF_UNIX
      # over AF_LOCAL.
      if family_int == ::Socket::AF_UNIX && family_int == ::Socket::AF_LOCAL
        return 'AF_UNIX'
      end

      ::Socket.constants.grep(/^AF_/).each do |name|
        return name.to_s if ::Socket.const_get(name) == family_int
      end

      'AF_UNSPEC'
    end

    def self.protocol_family_name(family_int)
      # Both PF_LOCAL and PF_UNIX use value 1. CRuby seems to prefer PF_UNIX
      # over PF_LOCAL.
      if family_int == ::Socket::PF_UNIX && family_int == ::Socket::PF_LOCAL
        return 'PF_UNIX'
      end

      ::Socket.constants.grep(/^PF_/).each do |name|
        return name.to_s if ::Socket.const_get(name) == family_int
      end

      'PF_UNSPEC'
    end

    def self.protocol_name(family_int)
      ::Socket.constants.grep(/^IPPROTO_/).each do |name|
        return name.to_s if ::Socket.const_get(name) == family_int
      end

      'IPPROTO_IP'
    end

    def self.socket_type_name(socktype)
      ::Socket.constants.grep(/^SOCK_/).each do |name|
        return name.to_s if ::Socket.const_get(name) == socktype
      end

      nil
    end

    def self.protocol_family(family)
      case family
      when Symbol, String
        f = family.to_s

        unless family_prefix?(f)
          f = 'PF_' + f
        end

        if ::Socket.const_defined?(f)
          ::Socket.const_get(f)
        else
          raise SocketError, "unknown socket domain: #{family}"
        end
      when Integer
        family
      when NilClass
        ::Socket::PF_UNSPEC
      else
        if family.respond_to?(:to_str)
          protocol_family(Primitive.convert_to_str(family))
        else
          raise SocketError, "unknown socket domain: #{family}"
        end
      end
    end

    def self.socket_type(type)
      case type
      when Symbol, String
        t = type.to_s

        if t[0..4] != 'SOCK_'
          t = "SOCK_#{t}"
        end

        if ::Socket.const_defined?(t)
          ::Socket.const_get(t)
        else
          raise SocketError, "unknown socket type: #{type}"
        end
      when Integer
        type
      when NilClass
        0
      else
        if type.respond_to?(:to_str)
          socket_type(Primitive.convert_to_str(type))
        else
          raise SocketError, "unknown socket type: #{type}"
        end
      end
    end

    def self.convert_reverse_lookup(socket = nil, reverse_lookup = nil)
      if Primitive.nil?(reverse_lookup)
        if socket
          reverse_lookup = !socket.do_not_reverse_lookup
        else
          reverse_lookup = !BasicSocket.do_not_reverse_lookup
        end

      elsif reverse_lookup == :hostname
        reverse_lookup = true

      elsif reverse_lookup == :numeric
        reverse_lookup = false

      elsif !Primitive.true?(reverse_lookup) and !Primitive.false?(reverse_lookup)
        raise ArgumentError,
          "invalid reverse_lookup flag: #{reverse_lookup.inspect}"
      end

      reverse_lookup
    end

    def self.address_info(method, socket, reverse_lookup = nil)
      sockaddr = Foreign.__send__(method, socket)

      reverse_lookup = convert_reverse_lookup(socket, reverse_lookup)

      options = ::Socket::Constants::NI_NUMERICHOST |
        ::Socket::Constants::NI_NUMERICSERV

      family, port, host, ip = Foreign
        .getnameinfo(sockaddr, options, reverse_lookup)

      [family, port.to_i, host, ip]
    end

    def self.shutdown_option(how)
      case how
      when String, Symbol
        prefixed_socket_constant(how.to_s, 'SHUT_') do
          "unknown shutdown argument: #{how}"
        end
      when Integer
        if how == ::Socket::SHUT_RD or
          how == ::Socket::SHUT_WR or
          how == ::Socket::SHUT_RDWR
          how
        else
          raise ArgumentError,
            'argument should be :SHUT_RD, :SHUT_WR, or :SHUT_RDWR'
        end
      else
        if how.respond_to?(:to_str)
          shutdown_option(coerce_to_string(how))
        else
          raise TypeError,
            "no implicit conversion of #{Primitive.class(how)} into Integer"
        end
      end
    end
  end
end
