# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --regexp-instrument-creation option" do
  it "works" do
    out = ruby_exe("/abc/i", options: "--experimental-options --regexp-instrument-creation")
    out.should.include? "Regular expression statistics"
    out.should.include? "1    /abc/i"
  end
end
