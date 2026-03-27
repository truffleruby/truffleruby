# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --warn-truffle-regex-match-fallback option" do
  it "works" do
    out = ruby_exe('2.times { /(?<loop>a\g<loop>|)/ =~ "aaa" }', options: "--experimental-options --warn-truffle-regex-match-fallback", args: "2>&1")
    out = out.gsub(/^.+: warning/, 'warning')
    out.should == <<~OUT
      warning: match_in_region(/(?<loop>a\\g<loop>|)/, "aaa"@UTF-8, 0, 3, false, 0) enc=US-ASCII cannot be run as a Truffle regexp and fell back to Joni
      warning: match_in_region(/(?<loop>a\\g<loop>|)/, "aaa"@UTF-8, 0, 3, false, 0) enc=US-ASCII cannot be run as a Truffle regexp and fell back to Joni
    OUT
  end
end
