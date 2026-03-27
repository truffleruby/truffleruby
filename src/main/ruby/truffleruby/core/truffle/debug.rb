# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle::Debug
  # Note: used by MSpec
  def self.show_backtraces
    $stderr.puts 'All Thread and Fiber backtraces:'
    Primitive.all_fibers_backtraces.each do |fiber, backtrace|
      $stderr.puts "#{fiber} of #{Primitive.fiber_thread(fiber)}", backtrace, nil
    end
  end

  class Breakpoint
    def initialize(handle)
      @handle = handle
    end

    def remove
      Truffle::Debug.remove_handle @handle
    end

  end

  def self.break(file, line, &block)
    Breakpoint.new(break_handle(File.expand_path(file), line, &block))
  end
end
