# frozen_string_literal: true
# truffleruby_primitives: true

#
# Copyright (C) 2008-2010 Wayne Meissner
#
# This file is part of ruby-ffi.
#
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of the Ruby FFI project nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
#

module FFI
  module LastError

    library = Primitive.interop_eval_nfi 'default'
    function_name = Truffle::Platform.darwin? ? :__error : :__errno_location
    # It would be tempting to make it return uint64,
    # but Truffle NFI always returns a pointer on Native Image for this function, regardless of the signature
    NFI_ERRNO_FUNCTION = Primitive.interop_eval_nfi('():pointer').bind(library[function_name])

    # @return [Numeric]
    # Get +errno+ value.
    def error
      nfi_errno_address = Primitive.interop_as_pointer(NFI_ERRNO_FUNCTION.call)
      Primitive.pointer_read_int(nfi_errno_address)
    end

    # @param [Numeric] error
    # @return [nil]
    # Set +errno+ value.
    def error=(error)
      nfi_errno_address = Primitive.interop_as_pointer(NFI_ERRNO_FUNCTION.call)
      Primitive.pointer_write_int(nfi_errno_address, error)
    end

    module_function :error, :error=
  end
end
