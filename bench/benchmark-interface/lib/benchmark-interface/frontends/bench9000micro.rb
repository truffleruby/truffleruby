# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

input = micro_harness_input

SMALL_PRIME = 149

Object.instance_eval do
  alias_method :user_micro_harness_sample, :micro_harness_sample
end

BenchmarkInterface.benchmark do
  user_micro_harness_sample input
end
