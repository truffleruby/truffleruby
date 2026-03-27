# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "TruffleRuby" do

  it "contains no other public methods" do
    (TruffleRuby.methods - Module.new.methods).sort.should == %i{
      full_memory_barrier graal? graalvm_home jit? native? revision sulong? cexts? synchronized
    }.sort
  end

end
