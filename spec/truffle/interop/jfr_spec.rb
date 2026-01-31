# Copyright (c) 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "JFR event streaming" do
  thread_start_event = "jdk.ThreadStart"

  it "can stream JFR events" do
    recording_stream_class = Java.type("jdk.jfr.consumer.RecordingStream")

    events_received = Queue.new

    stream = recording_stream_class.new

    stream.onEvent(thread_start_event) do |event|
      events_received << event
    end

    # Enable the event and start streaming asynchronously
    stream.enable(thread_start_event)
    stream.startAsync

    # Trigger a thread start event
    Thread.new { }.join

    # Wait for event to be received (with timeout)
    received = events_received.pop(timeout: 5)

    stream.close

    # Verify we received the expected ThreadStart event
    # NOTE: it's possible that the received event comes from a different thread,
    # but that is fine, as it still shows that runtime supports streaming JFR events.
    received.should_not be_nil
    received.getEventType.getName.should == thread_start_event
  end
end
