# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  
  class RBenchContext
    
    def initialize
      @columns = []
    end
    
    def format(options)
      # Ignore
    end
    
    def column(name, options=nil)
      singleton_class = (class << self; self end)
      singleton_class.class_eval "def #{name}(&block); rbench_benchmark #{name.inspect}, block; end"
      @columns.push name
    end
    
    def group(name)
      @group_name = name
      yield self
    ensure
      @group_name = nil
    end
    
    def report(name, &block)
      if @columns.size == 0
        BenchmarkInterface.benchmark name, &block
      else
        @report_name = name
        instance_eval &block
      end
    ensure
      @report_name = nil
    end
    
    def summary(title)
      # Ignore
    end
    
    def rbench_benchmark(column_name, block)
      name = [@group_name, @report_name, column_name].compact.join('-')
      BenchmarkInterface.benchmark name, &block
    end
    
  end
  
end

module RBench
  
  def self.run(count, &block)
    BenchmarkInterface::RBenchContext.new.instance_eval &block
  end
  
end
