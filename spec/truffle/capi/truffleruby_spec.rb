# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/optional/capi/spec_helper'

load_extension("truffleruby")

describe "C-API TRUFFLERUBY macro" do
  before :each do
    @s = CApiTruffleRubySpecs.new
  end

  it "is defined" do
    @s.truffleruby.should be_true
  end
end
