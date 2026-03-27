# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Fcntl
  F_DUPFD = Truffle::Config['platform.fcntl.F_DUPFD']
  F_DUPFD_CLOEXEC = Truffle::Config['platform.fcntl.F_DUPFD_CLOEXEC']
  F_GETFD = Truffle::Config['platform.fcntl.F_GETFD']
  F_GETLK = Truffle::Config['platform.fcntl.F_GETLK']
  F_SETFD = Truffle::Config['platform.fcntl.F_SETFD']
  F_GETFL = Truffle::Config['platform.fcntl.F_GETFL']
  F_SETFL = Truffle::Config['platform.fcntl.F_SETFL']
  F_SETLK = Truffle::Config['platform.fcntl.F_SETLK']
  F_SETLKW = Truffle::Config['platform.fcntl.F_SETLKW']
  FD_CLOEXEC = Truffle::Config['platform.fcntl.FD_CLOEXEC']
  F_RDLCK = Truffle::Config['platform.fcntl.F_RDLCK']
  F_UNLCK = Truffle::Config['platform.fcntl.F_UNLCK']
  F_WRLCK = Truffle::Config['platform.fcntl.F_WRLCK']
  O_CREAT = Truffle::Config['platform.file.O_CREAT']
  O_EXCL = Truffle::Config['platform.file.O_EXCL']
  O_NOCTTY = Truffle::Config['platform.file.O_NOCTTY']
  O_TRUNC = Truffle::Config['platform.file.O_TRUNC']
  O_APPEND = Truffle::Config['platform.file.O_APPEND']
  O_CLOEXEC = Truffle::Config['platform.file.O_CLOEXEC']
  O_NONBLOCK = Truffle::Config['platform.file.O_NONBLOCK']
  O_NDELAY = Truffle::Config['platform.file.O_NDELAY']
  O_RDONLY = Truffle::Config['platform.file.O_RDONLY']
  O_RDWR = Truffle::Config['platform.file.O_RDWR']
  O_WRONLY = Truffle::Config['platform.file.O_WRONLY']
  O_ACCMODE = Truffle::Config['platform.file.O_ACCMODE']
end
