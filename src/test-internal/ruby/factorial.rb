# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Beware, RubyDebugTest use hard-coded line numbers from this file!

def fac(n)
  if n <= 1
    1
  else
    nMinusOne = n - 1
    nMOFact = fac(nMinusOne)
    res = n * nMOFact
    res
  end
end

def main
  res = fac(2)
  res
end

Polyglot.export_method :main
