# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

input = harness_input

unless harness_verify(harness_sample(input))
  abort 'result was incorrect'
end

Object.instance_eval do
  alias_method :user_harness_sample, :harness_sample
end

BenchmarkInterface.benchmark do
  user_harness_sample input
end
