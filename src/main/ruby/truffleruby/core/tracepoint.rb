# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class TracePoint
  def self.trace(*events, &handler)
    TracePoint.new(*events, &handler).tap(&:enable)
  end

  def initialize(*events, &handler)
    events = [:line] if events.empty?
    events = events.map { |event| Primitive.convert_type event, Symbol, :to_sym }
    events.each do |event|
      case event
      when :line, :class, :end, :call, :c_call, :return, :fiber_switch, :thread_begin, :thread_end, :never
        # Supported event, keep it and pass it below
      else
        raise ArgumentError, "unknown event: #{event}"
      end
    end

    raise ArgumentError, 'must be called with a block' unless handler

    Primitive.tracepoint_initialize self, events.uniq, handler
  end

  # The object yielded to the block is a new TracePoint object per event, it is equal to its TracePoint
  def ==(other)
    Primitive.is_a?(other, TracePoint) &&
      Primitive.equal?(Primitive.trace_point_owner(self), Primitive.trace_point_owner(other))
  end
  alias_method :eql?, :==

  def hash
    owner = Primitive.trace_point_owner(self)
    if Primitive.equal?(owner, self)
      super
    else
      owner.hash
    end
  end

  def inspect
    if Primitive.trace_point_active_event?(self)
      case event
      when :call, :c_call, :return
        return "#<TracePoint:#{event} '#{method_id}' #{path}:#{lineno}>"
      when :thread_begin, :thread_end
        return "#<TracePoint:#{event} #{self.self.inspect}>"
      else
        return "#<TracePoint:#{event} #{path}:#{lineno}>"
      end
    end

    state = enabled? ? 'enabled' : 'disabled'
    "#<TracePoint:#{state}>"
  end
end
