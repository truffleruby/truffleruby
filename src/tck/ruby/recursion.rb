# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

def fact(n)
  raise ArgumentError if n < 0
  (1..n).reduce(1, :*)
end

def fib(n)
  raise ArgumentError if n < 0
  if n < 2
    n
  else
    fib(n-2) + fib(n-1)
  end
end

def ack(m, n)
  if m == 0
    n + 1
  elsif n == 0
    ack(m - 1, 1)
  else
    ack(m - 1, ack(m, n - 1))
  end
end

-> {
  [
    fact(10),
    fib(10),
    ack(3, 4)
  ]
}
