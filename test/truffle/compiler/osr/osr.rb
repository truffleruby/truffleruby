# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

abort 'not running the GraalVM Compiler' unless TruffleRuby.jit?

timeout = Process.clock_gettime(Process::CLOCK_MONOTONIC) + 60

begin
  while Process.clock_gettime(Process::CLOCK_MONOTONIC) < timeout
    Primitive.assert_not_compiled
  end

  puts 'while loop optimisation timed out'
  exit 1
rescue Truffle::GraalError => e
  if e.message.include? 'Primitive.assert_not_compiled'
    puts 'while loop optimising'
    exit 0
  else
    p e.message
    puts 'some other error'
    exit 1
  end
end

exit 1
