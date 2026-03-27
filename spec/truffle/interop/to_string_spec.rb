# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.to_string" do

  it "converts a boolean to a String" do
    Truffle::Interop.to_string(true).should == "true"
  end

  it "converts a primitive number to a String" do
    Truffle::Interop.to_string(14).should == "14"
  end

  it "converts an object to a String" do
    Truffle::Interop.to_string(Object.new).should =~ /^RubyBasicObject@\h+<Object>$/
  end

  it "converts a foreign object to a String" do
    Truffle::Interop.to_string(Truffle::Debug.foreign_object).should =~ /org\.truffleruby\.debug\.TruffleDebugNodes\$ForeignObjectNode\$ForeignObject@\h+/
  end

end
