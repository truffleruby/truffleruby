# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --truffle-regex-ignore-atomic-groups option" do
  it "works" do
    out = ruby_exe('/foo(?>(?:A)+)Abar/ =~ "fooAAAAAbar"', options: "--experimental-options --truffle-regex-ignore-atomic-groups --compare-regex-engines", args: "2>&1")
    out.should == <<~OUT
      match_in_region(/foo(?>(?:A)+)Abar/, "fooAAAAAbar"@UTF-8, 0, 11, false, 0) enc=? gave
          0 - 11
      but we expected
          NO MATCH
    OUT
  end
end
