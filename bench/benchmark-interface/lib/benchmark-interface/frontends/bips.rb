# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module BenchmarkInterface
  
  class BipsContext
    
    attr_accessor :compare, :hold, :time, :warmup, :iterations
    
    def config(options)
      # Ignore
    end
    
    def compare!
      # Ignore
    end
    
    def item(name=nil, code=nil, &block)
      raise 'cannot have both a string and a block' if code && block
      block = eval("Proc.new { #{code} }") if code
      BenchmarkInterface.benchmark name, &block
    end
    alias_method :report, :item
    
  end
  
  module BipsFrontend
    def ips
      yield BenchmarkInterface::BipsContext.new
    end
  end

end

module Benchmark
  extend BenchmarkInterface::BipsFrontend
end
