# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# TODO CS 3-Feb-16 Not compliant with MRI - here as a regression test

require_relative 'backtraces'

def m1
  eval 'm2'
end

def m2
  eval 'm3', binding, 'a_m3_calling_source.rb', 42
end

def m3
  eval "raise 'message'"
end

check('eval.backtrace') do
  m1
end
