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
    module AncillaryData
      LEVEL_PREFIXES = {
        ::Socket::SOL_SOCKET   => %w{SCM_ UNIX},
        ::Socket::IPPROTO_IP   => %w{IP_ IP},
        ::Socket::IPPROTO_IPV6 => %w{IPV6_ IPV6},
        ::Socket::IPPROTO_TCP  => %w{TCP_ TCP},
        ::Socket::IPPROTO_UDP  => %w{UDP_ UDP}
      }

      # MRI: rsock_level_arg()
      def self.level(raw_level)
        if Primitive.is_a?(raw_level, Integer)
          raw_level
        elsif level = string_arg(raw_level)
          if level == 'SOL_SOCKET' or level == 'SOCKET'
            ::Socket::SOL_SOCKET

          # Translates "TCP" into "IPPROTO_TCP", "UDP" into "IPPROTO_UDP", etc.
          else
            Socket.prefixed_socket_constant(level, 'IPPROTO_') do
              "unknown protocol level: #{level}"
            end
          end
        else
          Primitive.rb_num2int(raw_level)
        end
      end

      # MRI: rsock_cmsg_type_arg()
      def self.type(family, level, raw_type)
        if Primitive.is_a?(raw_type, Integer)
          raw_type
        elsif type = string_arg(raw_type)
          if family == ::Socket::AF_INET or family == ::Socket::AF_INET6
            prefix, label = LEVEL_PREFIXES[level]
          else
            prefix, label = LEVEL_PREFIXES[::Socket::SOL_SOCKET]
          end

          # Translates "RIGHTS" into "SCM_RIGHTS", "CORK" into "TCP_CORK" (when
          # the level is IPPROTO_TCP), etc.
          if prefix and label
            Socket.prefixed_socket_constant(type, prefix) do
              "Unknown #{label} control message: #{type}"
            end
          else
            raise TypeError,
              "no implicit conversion of #{Primitive.class(type)} into Integer"
          end
        else
          Primitive.rb_num2int(raw_type)
        end
      end

      # MRI: constant_arg() looks up a Symbol or an object converted with rb_check_string_type() by name,
      # and converts anything else with NUM2INT(). This returns the name, or nil for the NUM2INT() case.
      def self.string_arg(arg)
        if Primitive.is_a?(arg, Symbol)
          arg.to_s
        else
          Truffle::Type.rb_check_convert_type(arg, String, :to_str)
        end
      end
    end
  end
end
