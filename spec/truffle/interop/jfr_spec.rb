# Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "JFR event streaming" do
  it "can stream JFR events" do
    recording_stream_class = Java.type("jdk.jfr.consumer.RecordingStream")

    gc_count = 3
    events_received = Queue.new

    stream = recording_stream_class.new

    # Register event handler for GC events
    stream.onEvent("jdk.GarbageCollection") do |event|
      events_received << event
    end

    # Enable the event and start streaming asynchronously
    stream.enable("jdk.GarbageCollection")
    stream.startAsync

    # Trigger garbage collection to generate events
    gc_count.times { GC.start }

    # Wait for all events to be received (with timeout)
    received = []
    gc_count.times do
      received << events_received.pop(timeout: 5)
    end

    stream.close

    # Verify we received the expected GC events
    received.size.should eql(gc_count)
    received.each do |event|
      event.getEventType.getName.should == "jdk.GarbageCollection"
    end
  end
end
