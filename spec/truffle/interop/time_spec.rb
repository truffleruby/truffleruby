# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Ruby Time instances" do
  it "respond to interop date and time-related messages" do
    time = Time.now
    Truffle::Interop.should.date?(time)
    Truffle::Interop.should.time?(time)
    Truffle::Interop.should.time_zone?(time)
    Truffle::Interop.should.instant?(time)

    Truffle::Interop.should_not.duration?(time)
  end
end
