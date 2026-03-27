# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  
  class BenchmarkContext
    
    def report(name=nil, &block)
      BenchmarkInterface.benchmark name, &block
    end
    
  end
  
end

module Benchmark

  CAPTION = "      user     system      total        real\n"
  FORMAT  = "%10.6u %10.6y %10.6t %10.6r\n"

  def self.measure(name=nil, &block)
    BenchmarkInterface.benchmark name, &block
  end

  def self.realtime(name=nil, &block)
    BenchmarkInterface.benchmark name, &block
  end

  def self.benchmark(caption='', label_width=nil, format=nil, *labels)
    yield BenchmarkInterface::BenchmarkContext.new
  end
  
  def self.bm(label_width=0, *labels)
    yield BenchmarkInterface::BenchmarkContext.new
  end

  def self.bmbm(label_width=0)
    yield BenchmarkInterface::BenchmarkContext.new
  end
  
end
