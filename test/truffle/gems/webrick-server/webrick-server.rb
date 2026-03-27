# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'webrick'

server = WEBrick::HTTPServer.new(BindAddress: '127.0.0.1', Port: 0)

server.mount_proc '/' do |req, res|
  res.body = "Hello, world!\n"
end

server.start
