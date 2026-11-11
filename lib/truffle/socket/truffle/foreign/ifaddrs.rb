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
    module Foreign
      # Class representing an "ifaddrs" C structure.
      #
      # The memory used by this structure should be free'd using
      # `Truffle::Socket::Foreign.freeifaddrs` _only_ as following:
      #
      #     Truffle::Socket::Foreign.freeifaddrs(initial_ifaddrs_struct)
      #
      # To ensure Rubinius doesn't accidentally free invalid pointers all
      # pointers (including the pointer of "self") stored in this class have the
      # "autorelease" option set to false.
      class Ifaddrs < ::FFI::Struct
        Truffle::Socket.config(self, 'platform.ifaddrs', :ifa_next, :ifa_name, :ifa_flags,
               :ifa_addr, :ifa_netmask, :ifa_broadaddr, :ifa_dstaddr)

        POINTERS = [
          :address, :next, :broadcast_address, :destination_address,
          :netmask_address
        ]

        def initialize(*args)
          super

          POINTERS.each do |name|
            pointer = __send__(name)

            pointer.autorelease = false if pointer
          end

          pointer.autorelease = false
        end

        def name
          self[:ifa_name]
        end

        def flags
          self[:ifa_flags]
        end

        def data
          @data ||= self[:ifa_data]
        end

        def next
          @next ||= self[:ifa_next]
        end

        def address
          @address ||= self[:ifa_addr]
        end

        def broadcast_address
          @broadcast_address ||= self[:ifa_broadaddr]
        end

        def destination_address
          @destination_address ||= self[:ifa_dstaddr]
        end

        def netmask_address
          @netmask_address ||= self[:ifa_netmask]
        end

        def each_address
          struct = self

          while struct
            yield struct

            next_pointer = struct.next
            struct = next_pointer.null? ? nil : Primitive.class(self).new(next_pointer)
          end
        end

        def broadcast?
          flags & ::Socket::IFF_BROADCAST > 0
        end

        def point_to_point?
          flags & ::Socket::IFF_POINTOPOINT > 0
        end

        def address_to_addrinfo
          return if address.null?

          sockaddr = Sockaddr.new(address)

          if sockaddr.family == ::Socket::AF_INET
            ::Addrinfo.new(SockaddrIn.new(address).to_s)
          elsif sockaddr.family == ::Socket::AF_INET6
            ::Addrinfo.new(SockaddrIn6.new(address).to_s)
          else
            nil
          end
        end

        def broadcast_to_addrinfo
          return if !broadcast? || broadcast_address.null?

          ::Addrinfo.raw_with_family(Sockaddr.new(broadcast_address).family)
        end

        def destination_to_addrinfo
          return if !point_to_point? || destination_address.null?

          ::Addrinfo.raw_with_family(Sockaddr.new(destination_address).family)
        end

        def netmask_to_addrinfo
          return if netmask_address.null?

          sockaddr = Sockaddr.new(netmask_address)

          if sockaddr.family == ::Socket::AF_INET
            ::Addrinfo.new(SockaddrIn.new(netmask_address).to_s)
          elsif sockaddr.family == ::Socket::AF_INET6
            ::Addrinfo.new(SockaddrIn6.new(netmask_address).to_s)
          else
            ::Addrinfo.raw_with_family(sockaddr.family)
          end
        end
      end
    end
  end
end
