# frozen_string_literal: true

# Copyright (c) 2015, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

module Truffle::GCOperations
  def self.stat_hash(key_or_hash)
    if Primitive.is_a?(key_or_hash, Symbol)
      case key_or_hash
      when :time
        Primitive.gc_stat[0]
      when :count
        Primitive.gc_stat[1]
      when :minor_gc_count
        Primitive.gc_stat[2]
      when :major_gc_count
        Primitive.gc_stat[3]
      when :unknown_count
        Primitive.gc_stat[4]
      when :used, :heap_live_slots
        Primitive.gc_stat[5]
      when :committed, :heap_available_slots
        Primitive.gc_stat[6]
      when :heap_free_slots
        stat = Primitive.gc_stat
        stat[6] - stat[5]
      when :init
        Primitive.gc_stat[7]
      when :max
        Primitive.gc_stat[8]
      else
        # differs from MRI, more compatible than raising argument error, use GC.stat.key? to check if key is supported
        0
      end
    else
      time, count, minor_count, major_count, unknown_count, used, committed, init, max = Primitive.gc_stat
      # Initialize stat for statistics that come from memory pools, and populate it with some final stats (ordering similar to MRI)
      key_or_hash[:time] = time
      key_or_hash[:count] = count
      key_or_hash[:minor_gc_count] = minor_count
      key_or_hash[:major_gc_count] = major_count
      key_or_hash[:unknown_count] = unknown_count # if nonzero, major or minor count needs to be updated for this GC case
      key_or_hash[:used] = used
      key_or_hash[:heap_live_slots] = used
      key_or_hash[:committed] = committed
      key_or_hash[:heap_available_slots] = committed
      key_or_hash[:heap_free_slots] = committed - used
      key_or_hash[:init] = init
      key_or_hash[:max] = max
      # differs from MRI, more compatible than raising argument error, use GC.stat.key? to check if key is supported
      key_or_hash.default = 0
      key_or_hash
    end
  end
end

module GC
  def self.run(force)
    start
  end

  def self.start(full_mark: true, immediate_mark: true, immediate_sweep: true)
    Primitive.gc_start()
  end

  # Totally fake.
  @stress_level = false

  # Totally fake.
  def self.stress
    @stress_level
  end

  # Totally fake.
  def self.stress=(flag)
    @stress_level = Primitive.as_boolean(flag)
  end

  # Totally fake.
  @enabled = true

  def self.enable
    # We don't support disable, so sure! enabled!
    ret = !@enabled
    @enabled = true
    ret
  end

  # Totally fake.
  def self.disable
    # Treat this like a request that we don't honor.
    ret = !@enabled
    @enabled = false
    ret
  end

  # Totally fake.
  @auto_compact = false

  # Totally fake.
  def self.auto_compact
    @auto_compact
  end

  # Totally fake.
  def self.auto_compact=(flag)
    @auto_compact = Primitive.as_boolean(flag)
  end

  # Totally fake.
  @measure_total_time = true

  def self.measure_total_time
    @measure_total_time
  end

  def self.measure_total_time=(flag)
    @measure_total_time = Primitive.as_boolean(flag)
    flag
  end

  def self.total_time
    Primitive.gc_time * 1_000_000
  end

  def self.config(hash = nil)
    if Primitive.nil?(hash)
      { implementation: 'default' }
    else
      raise ArgumentError unless Primitive.is_a?(hash, Hash)
      hash.each_key do |key|
        if key == :implementation
          raise ArgumentError, 'Attempting to set read-only key "Implementation"'
        end
      end
      {}
    end
  end

  def garbage_collect(...)
    GC.start(...)
  end

  def self.stat(key = nil)
    case key
    when NilClass
      Truffle::GCOperations.stat_hash({})
    when Symbol, Hash
      Truffle::GCOperations.stat_hash(key)
    else
      raise TypeError, 'non-hash or symbol given'
    end
  end

  def self.heap_stats
    memory_pool_names, memory_pool_info = Primitive.gc_heap_stats

    stat = GC.stat
    memory_pool_names.each_with_index do |memory_pool_name, i|
      # Populate memory pool specific stats
      info = memory_pool_info[i]
      if info
        stat[memory_pool_name] = data = {
            used: info[0],
            committed: info[1],
            init: info[2],
            max: info[3],
            peak_used: info[4],
            peak_committed: info[5],
            peak_init: info[6],
            peak_max: info[7],
            last_used: info[8],
            last_committed: info[9],
            last_init: info[10],
            last_max: info[11],
        }

        # Calculate stats across memory pools for peak_/last_ (we already know the values for current usage)
        data.each_pair do |k,v|
          stat[k] += v if k.start_with?('peak_', 'last_')
        end
      end
    end

    stat
  end

  module Profiler
    @enabled = true
    @since   = 0

    def self.clear
      @since = Primitive.gc_time
      nil
    end

    def self.disable
      # Treat this like a request that we don't honor.
      ret = !@enabled
      @enabled = false
      ret
    end

    def self.enable
      # We don't support disable, so sure! enabled!
      ret = !@enabled
      @enabled = true
      ret
    end

    def self.enabled?
      @enabled
    end

    def self.report(out = $stdout)
      out.write result
    end

    def self.result
      <<-OUT
Complete process runtime statistics
===================================
Collections: #{GC.count}
Total time (ms): #{Primitive.gc_time}
      OUT
    end

    def self.total_time
      (Primitive.gc_time - @since) / 1000.0
    end
  end
end
