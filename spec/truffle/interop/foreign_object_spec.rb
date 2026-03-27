# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Foreign objects" do
  it "can be printed with #puts" do
    foreign = Truffle::Debug.foreign_object
    -> {
      puts foreign
    }.should output_to_fd("#{foreign}\n")
  end

  it "can be printed with #p" do
    foreign = Truffle::Debug.foreign_object
    -> {
      p foreign
    }.should output_to_fd("#{foreign.inspect}\n")
  end
end
