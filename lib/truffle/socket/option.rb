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
  class Option
    attr_reader :family, :level, :optname, :data

    def self.bool(family, level, optname, bool)
      new(family, level, optname, [bool ? 1 : 0].pack('i'))
    end

    def self.int(family, level, optname, integer)
      new(family, level, optname, [integer].pack('i'))
    end

    def self.linger(onoff, secs)
      linger = Truffle::Socket::Foreign::Linger.new

      begin
        # MRI: rb_check_to_integer(), which is nil for an object that does not convert with #to_int
        linger.on_off = Truffle::Type.rb_check_to_integer(onoff, :to_int) || onoff
        linger.linger = secs

        new(:UNSPEC, :SOCKET, :LINGER, linger.to_s)
      ensure
        linger.pointer.free
      end
    end

    def initialize(family, level, optname, data)
      @family  = Truffle::Socket.address_family(family)
      @level   = Truffle::Socket::SocketOptions.socket_level(level, @family)
      @optname = Truffle::Socket::SocketOptions.socket_option(@level, optname)
      @data    = Primitive.convert_with_to_str(data)
    end

    def unpack(template)
      @data.unpack template
    end

    def inspect
      "#<#{Primitive.class(self)}: #@family_name #@level_name #@opt_name #{@data.inspect}>"
    end

    def bool
      expected_size = Truffle::Socket::Foreign::SIZEOF_INT

      unless @data.bytesize == expected_size
        raise TypeError,
          "invalid size, expected #{expected_size} but got #{@data.bytesize}"
      end

      if @data.unpack('i')[0] > 0
        true
      else
        false
      end
    end

    def int
      expected_size = Truffle::Socket::Foreign::SIZEOF_INT

      unless @data.bytesize == expected_size
        raise TypeError,
          "invalid size, expected #{expected_size} but got #{@data.bytesize}"
      end

      @data.unpack('i')[0]
    end

    def linger
      if @level != Socket::SOL_SOCKET || @optname != Socket::SO_LINGER
        raise TypeError, 'linger socket option expected'
      end

      expected_size = Truffle::Socket::Foreign::Linger.size

      if @data.bytesize != expected_size
        raise TypeError,
          "invalid size, expected #{expected_size} but got #{@data.bytesize}"
      end

      linger = Truffle::Socket::Foreign::Linger.from_string(@data)
      onoff  = nil

      case linger.on_off
      when 0
        onoff = false
      when 1
        onoff = true
      else
        onoff = linger.on_off.to_i
      end

      [onoff, linger.linger]
    end

    alias_method :to_s, :data
  end
end
