# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2023 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# This file is only used if the ffi gem is not activated, when using FFI as a
# stdlib. Note that older versions of the FFI gem would end up removing itself
# from $LOAD_PATH and then require this file.

# Require the pure-Ruby Truffle NFI backend
require_relative 'truffle/ffi_backend'

# Require our copy in stdlib of the FFI gem Ruby files
require_relative 'ffi/ffi'
