# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'benchmark-interface/version'
require 'benchmark-interface/timing'
require 'benchmark-interface/benchmark'
require 'benchmark-interface/benchmark-api'
require 'benchmark-interface/benchmark-set'
require 'benchmark-interface/frontends/mri'
require 'benchmark-interface/backends/simple'
require 'benchmark-interface/backends/stable'
require 'benchmark-interface/backends/fixed-iterations'
require 'benchmark-interface/backends/benchmark'
require 'benchmark-interface/backends/bips'
require 'benchmark-interface/backends/bench9000'
require 'benchmark-interface/backends/deep'
require 'benchmark-interface/require'
require 'benchmark-interface/run'

module BenchmarkInterface
  extend Timing

  def self.benchmark(name=nil, &block)
    BenchmarkInterface::BenchmarkSet.current.register name, block
  end

  def self.run_n_iterations(iterations)
    i = 0
    while i < iterations
      result = yield
      i += 1
    end
    result
  end
end

def benchmark(name=nil, &block)
  BenchmarkInterface.benchmark name, &block
end

if File.basename($PROGRAM_NAME) != 'benchmark'
  set = BenchmarkInterface::BenchmarkSet.new
  backend = BenchmarkInterface::Backends::Bips

  at_exit do
    set.prepare
    backend.run set, set.benchmarks, {}
  end
end
