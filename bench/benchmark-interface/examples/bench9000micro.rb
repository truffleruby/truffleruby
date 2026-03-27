# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require File.expand_path('clamp', File.dirname(__FILE__))

def micro_harness_input
  [10, 40, 90]
end

def micro_harness_iterations
  50_000_000
end

def micro_harness_sample(input)
  clamp_a(*input)
end

def micro_harness_expected
  sum = 0
  micro_harness_iterations.times do
    sum = (sum + 40) % 149
  end
  sum
end

require 'bench9000/micro-harness'
