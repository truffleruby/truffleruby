# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'objspace'

require_relative '../../ruby/spec_helper'

describe "ObjectSpace.define_finalizer" do

  # These specs require that the GC runs a full stop-the-world collection that
  # will push references on Runtime#gc(). All GCs on HotSpot and the SVM do
  # this, apart from the Epsilon collector on HotSpot. The remaining issue is
  # that the references are popped asynchronously at the JVM level, and then
  # from there popped again asynchronously in TruffleRuby, so we do still need
  # to wait with a timeout in order to assert that it has been done.
  # Truffle::Debug.drain_finalization_queue, which drains the queue in
  # TruffleRuby in the foreground, does seem to help increase test throughput.

  # NOTE(norswap, 30 Jul 2020): This caused sporadic transients in the gate, so I've disabled it with a failing tag.
  #   The assumption is that this works as expected but is just hard to test.

  it "will call the finalizer" do
    queue = Queue.new
    finalizer = proc {
      queue << :finalized
    }
    Object.new.tap do |object|
      ObjectSpace.define_finalizer object, finalizer
      ObjectSpace.reachable_objects_from(object).should include(finalizer)
    end
    Primitive.gc_force
    Truffle::Debug.drain_finalization_queue   # Not needed for correctness
    queue.pop(timeout: TIME_TOLERANCE).should == :finalized
  end

end
