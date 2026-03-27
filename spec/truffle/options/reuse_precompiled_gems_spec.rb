# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --reuse-precompiled-gems option" do
  it "adds listed gems into a Gem::Platform::REUSE_AS_BINARY_ON_TRUFFLERUBY list" do
    ruby_exe("p Gem::Platform::REUSE_AS_BINARY_ON_TRUFFLERUBY", options: "--experimental-options --reuse-precompiled-gems=foo,bar").should.include? '"foo", "bar"'
  end
end
