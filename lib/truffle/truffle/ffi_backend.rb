# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'rbconfig'

module FFI
  AbstractMemory = ::Truffle::FFI::AbstractMemory
  Pointer = ::Truffle::FFI::Pointer
  MemoryPointer = ::Truffle::FFI::MemoryPointer
  NullPointerError = ::Truffle::FFI::NullPointerError

  # Redefine Pointer.find_type_size to consider FFI types
  class Pointer
    def self.find_type_size(type)
      ::FFI.type_size(::FFI.find_type(type))
    end
  end

  module Platform
    # eregon: I would like to use rbconfig/sizeof here, but the linker requires
    # ffi, and the linker is needed to build the rbconfig/sizeof C extension,
    # so we need to break the cycle.
    ADDRESS_SIZE = 64
    LONG_SIZE = 64

    if ::Truffle::Platform.linux?
      # Set it so FFI::Library finds the right file directly, like on MRI,
      # and does not need to try and fail to load the /lib64/libc.so loader script.
      GNU_LIBC = 'libc.so.6'
    end
  end

  TypeDefs = {}
end

require_relative 'ffi_backend/last_error'
require_relative 'ffi_backend/type'
require_relative 'ffi_backend/struct_layout'
require_relative 'ffi_backend/struct'
require_relative 'ffi_backend/buffer'
require_relative 'ffi_backend/function'
require_relative 'ffi_backend/dynamic_library'
