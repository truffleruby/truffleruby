# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.mime_type_supported?" do

  it "returns true for application/x-ruby" do
    Truffle::Interop.mime_type_supported?('application/x-ruby').should be_true
  end

  it "returns false for application/x-this-language-does-not-exist" do
    Truffle::Interop.mime_type_supported?('application/x-this-language-does-not-exist').should be_false
  end

end
