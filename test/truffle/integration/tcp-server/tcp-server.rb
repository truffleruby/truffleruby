# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'socket'

server = TCPServer.new('127.0.0.1', 0)

loop do
  socket = server.accept

  begin
    request = "Hello, World from TruffleRuby! #{socket.gets}"

    socket.print "HTTP/1.1 200 OK\r\n" +
                 "Content-Type: text/plain\r\n" +
                 "Content-Length: #{request.bytesize}\r\n" +
                 "Connection: close\r\n\r\n"
    socket.print request
    socket.print "\n"
  ensure
    socket.close
  end
end
