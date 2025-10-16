# frozen_string_literal: true

# Copyright (c) 2014, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle::Platform
  # Used by Truffle::FFI
  L64 = true

  LONG_MIN = -0x8000000000000000
  LONG_MAX =  0x7fffffffffffffff

  # jnr-posix hard codes this value
  PATH_MAX = 1024

  HOST_OS = Truffle::System.host_os

  IS_LINUX = HOST_OS == 'linux'
  IS_DARWIN = HOST_OS == 'darwin'
  IS_BSD = HOST_OS == 'freebsd' || HOST_OS == 'netbsd' || HOST_OS == 'openbsd'
  IS_WINDOWS = HOST_OS == 'mswin32'

  SOEXT = IS_DARWIN ? 'dylib' : 'so'
  DLEXT = IS_DARWIN ? 'bundle' : 'so'

  ARCH = Truffle::System.host_cpu
  IS_AMD64 = ARCH == 'x86_64'
  IS_AARCH64 = ARCH == 'aarch64' || ARCH == 'arm64'

  def self.linux?
    IS_LINUX
  end

  def self.darwin?
    IS_DARWIN
  end

  def self.bsd?
    IS_BSD
  end

  def self.windows?
    IS_WINDOWS
  end

  def self.has_lchmod?
    bsd? || darwin?
  end

  def self.amd64?
    IS_AMD64
  end

  def self.aarch64?
    IS_AARCH64
  end
end
