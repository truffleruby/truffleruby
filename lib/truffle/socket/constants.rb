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
  module Constants
    all_valid = Truffle::Socket.constant_pairs

    all_valid.each { |name, value| const_set name, Integer(value) }

    # MRI compat. socket is a pretty screwed up API. All the constants in Constants
    # must also be directly accessible on Socket itself. This means it's not enough
    # to include Constants into Socket, because Socket#const_defined? must be able
    # to see constants like AF_INET6 directly on Socket, but #const_defined? doesn't
    # check inherited constants. O_o
    #
    all_valid.each { |name, value| Socket.const_set name, Integer(value) }


    afamilies = all_valid.to_a.select { |name,| name =~ /^AF_/ }
    afamilies.map! { |name, value| [value.to_i, name] }

    pfamilies = all_valid.to_a.select { |name,| name =~ /^PF_/ }
    pfamilies.map! { |name, value| [value.to_i, name] }

    AF_TO_FAMILY = Hash[*afamilies.flatten]
    PF_TO_FAMILY = Hash[*pfamilies.flatten]

    # MRI defines these constants manually, thus our FFI generators don't pick
    # them up.
    # Truffle: added unless const_defined? to avoid warnings
    EAI_ADDRFAMILY = 1 unless const_defined?(:EAI_ADDRFAMILY)
    EAI_NODATA = 7 unless const_defined?(:EAI_NODATA)
    IPPORT_USERRESERVED = 5000 unless const_defined?(:IPPORT_USERRESERVED)

    # This constant is hidden behind a #ifdef __GNU on Linux, meaning it won't
    # be available when using clang.
    unless const_defined?(:SCM_CREDENTIALS)
      SCM_CREDENTIALS = 2
    end
  end

  [:EAI_ADDRFAMILY, :EAI_NODATA, :IPPORT_USERRESERVED, :SCM_CREDENTIALS].each do |const|
    # Truffle: added unless const_defined? to avoid warnings
    const_set(const, Constants.const_get(const)) unless const_defined?(const)
  end

  class ResolutionError < SocketError
    attr_reader :error_code

    def initialize(message, error_code)
      super(message)
      @error_code = error_code
    end
  end
end
