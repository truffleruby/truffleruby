# truffleruby_primitives: true
#   (to nil out references to make unreachable)

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Class#subclasses" do
  it "doesn't prevent garbage collecting of the subclasses" do
    klass = Class.new

    1000.times { Class.new(klass) }
    Primitive.gc_force

    klass.subclasses.size.should == 0
  end
end
