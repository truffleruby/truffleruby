# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
#
require_relative '../../ruby/optional/capi/spec_helper'

load_extension("truffleruby_rbasic")

describe "RBasic support" do
  it "should raise an ArgumentError for unsupported flags" do
    specs = CApiTruffleRubyRBasicSpecs.new
    obj = Object.new

    -> {
      specs.set_flags(obj, specs.finalize_flag)
    }.should raise_error(ArgumentError, 'unsupported remaining flags: RUBY_FL_FINALIZE (1<<7)')

    -> {
      specs.set_flags(obj, specs.promoted_flag)
    }.should raise_error(ArgumentError, 'unsupported remaining flags: RUBY_FL_PROMOTED (1<<5)')

    -> {
      specs.set_flags(obj, 1 << 3)
    }.should raise_error(ArgumentError, 'unsupported remaining flags: unknown flag (8)')
  end

  it "should raise an ArgumentError when trying to unfreeze an object" do
    specs = CApiTruffleRubyRBasicSpecs.new
    obj = Object.new
    obj.freeze

    -> { specs.set_flags(obj, 0) }.should(raise_error(ArgumentError, 'can\'t unfreeze object'))
  end
end
