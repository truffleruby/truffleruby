# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.has_array_elements?" do

  it "array has size" do
    Truffle::Interop.has_array_elements?([1, 2, 3]).should be_true
  end

  { hash: {},
    string: "",
    integer: 1,
    method: nil.method(:inspect),
    lambda: -> { },
    class: Hash,
    struct: Struct.new(:a).new(:v)
  }.each do |name, v|
    it "#{name} does not have size" do
      Truffle::Interop.has_array_elements?(v).should be_false
    end
  end
end

describe "Truffle::Interop.size" do

  it "returns the size of an array" do
    Truffle::Interop.array_size([1, 2, 3]).should == 3
  end

  it "returns the size of any object with a size method" do
    obj = TruffleInteropSpecs::PolyglotArray.new
    obj.polyglot_write_array_element 1, true
    Truffle::Interop.array_size(obj).should == 2
  end

end
