# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  class BenchmarkAPI
    attr_reader :benchmark

    def initialize(benchmark)
      @benchmark = benchmark
    end

    def verify(&block)
      @benchmark.verify_block = block
    end
  end
end
