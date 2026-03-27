# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Kernel#gem" do
  it "does not raise an error for included gems" do
    require "rubygems"
    gem("json").should be_true_or_false
    gem("minitest").should be_true_or_false
    gem("power_assert").should be_true_or_false
    gem("psych").should be_true_or_false
    gem("rake").should be_true_or_false
    gem("rdoc").should be_true_or_false
  end
end
