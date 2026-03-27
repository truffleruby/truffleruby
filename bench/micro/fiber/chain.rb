# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

def create_chain(size)
  last = Fiber.new { loop { Fiber.yield 42 } }
  (size - 1).times.inject(last) { |nxt, _|
    Fiber.new { loop { Fiber.yield nxt.resume } }
  }
end

small = create_chain(10)

benchmark 'fiber-chain-small' do
  small.resume
end

