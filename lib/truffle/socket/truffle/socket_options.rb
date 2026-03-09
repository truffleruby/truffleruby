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
    module SocketOptions
      def self.getsockopt(socket, family, level, optname)
        level = socket_level(level, family)
        optname = socket_option(level, optname)
        data = Truffle::Socket::Foreign.getsockopt(Primitive.io_fd(socket), level, optname)

        ::Socket::Option.new(family, level, optname, data)
      end

      def self.socket_level(level, family = nil)
        if Primitive.is_a?(level, Symbol) or Primitive.is_a?(level, String)
          if ::Socket.const_defined?(level, false) # Truffle: added inherit false
            ::Socket.const_get(level, false) # Truffle: added inherit false
          else
            if family and is_ip_family?(family)
              ip_level_to_int(level)
            elsif level.to_s == 'SOCKET'
              ::Socket::SOL_SOCKET
            else
              constant('IPPROTO', level)
            end
          end
        elsif level.respond_to?(:to_str)
          socket_level(Socket.coerce_to_string(level), family)
        else
          Primitive.convert_with_to_int level
        end
      end

      def self.socket_option(level, optname)
        case optname
        when Symbol, String
          if ::Socket.const_defined?(optname, false) # Truffle: added inherit false
            ::Socket.const_get(optname, false) # Truffle: added inherit false
          else
            case level
            when ::Socket::SOL_SOCKET
              constant('SO', optname)
            when ::Socket::IPPROTO_IP
              constant('IP', optname)
            when ::Socket::IPPROTO_TCP
              constant('TCP', optname)
            when ::Socket::IPPROTO_UDP
              constant('UDP', optname)
            when ::Socket.const_defined?(:IPPROTO_IPV6, false) && ::Socket::IPPROTO_IPV6 # Truffle: added inherit false
              constant('IPV6', optname)
            else
              raise SocketError,
                "Unsupported socket level option name: #{optname}"
            end
          end
        else
          if optname.respond_to?(:to_str)
            socket_option(level, Socket.coerce_to_string(optname))
          else
            Primitive.convert_with_to_int optname
          end
        end
      end

      def self.is_ip_family?(family)
        family == 'AF_INET' || family == 'AF_INET6'
      end

      def self.ip_level_to_int(level)
        prefixes = ['IPPROTO', 'SOL', 'IPV6']

        prefixes.each do |prefix|
          const = "#{prefix}_#{level}"

          if ::Socket.const_defined?(const, false) # Truffle: added inherit false
            return ::Socket.const_get(const, false) # Truffle: added inherit false
          end
        end

        nil
      end

      def self.constant(prefix, suffix)
        const = "#{prefix}_#{suffix}"

        if ::Socket.const_defined?(const, false) # Truffle: added inherit false
          ::Socket.const_get(const, false) # Truffle: added inherit false
        else
          raise SocketError, "Undefined socket constant: #{const}"
        end
      end
    end
  end
end
