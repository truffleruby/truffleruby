# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.eval" do

  it "can eval ruby code" do
    Truffle::Interop.eval("application/x-ruby", "2 + 3").should == 5
  end

  it "raises an ArgumentError if the language is not found" do
    -> {
      Truffle::Interop.eval("application/language-not-exist", "code")
    }.should raise_error(ArgumentError, /No language for id application\/language-not-exist found/)
  end

end
