# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  
  def self.require_rubygems
    begin
      Kernel.class_eval do
        alias_method :require, :benchmark_interface_original_require
        require 'rubygems'
        alias_method :benchmark_interface_original_require, :require
      end
    rescue LoadError
    end
  end

end

module Kernel

  alias_method :benchmark_interface_original_require, :require

  def require(feature)
    case feature
      when 'benchmark'
        benchmark_interface_original_require 'benchmark-interface/frontends/benchmark'
      when 'benchmark/ips'
        benchmark_interface_original_require 'benchmark-interface/frontends/bips'
      when 'rbench'
        benchmark_interface_original_require 'benchmark-interface/frontends/rbench'
      when 'perfer'
        benchmark_interface_original_require 'benchmark-interface/frontends/perfer'
      when 'bench9000/harness', 'bench9000/micro-harness'
        if BenchmarkInterface::Backends::Bench9000.loading_real?
          # Do nothing
        elsif feature == 'bench9000/harness'
          benchmark_interface_original_require 'benchmark-interface/frontends/bench9000'
        elsif feature == 'bench9000/micro-harness'
          benchmark_interface_original_require 'benchmark-interface/frontends/bench9000micro'
        end
      when 'benchmark-interface'
        # Already loaded by bin/benchmark
      else
        benchmark_interface_original_require feature
    end
  end

end
