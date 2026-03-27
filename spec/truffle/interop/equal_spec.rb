# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Calling #equal? on a foreign object" do

  it "tests reference equality for an object which has an identity" do
    a = Truffle::Debug.foreign_object
    Truffle::Interop.should.has_identity?(a)
    a.equal?(a).should be_true

    b = Truffle::Debug.foreign_object
    a.equal?(b).should be_false
  end

  it "tests reference equality for an object which has no identity" do
    a = Truffle::Debug.foreign_object_with_members
    Truffle::Interop.should_not.has_identity?(a)
    a.equal?(a).should be_true

    b = Truffle::Debug.foreign_object_with_members
    a.equal?(b).should be_false
  end

  guard -> { !TruffleRuby.native? } do
    it "looks at the underlying object for Java interop" do
      big_integer = Truffle::Interop.java_type("java.math.BigInteger")
      a = big_integer[:ONE]
      b = big_integer[:ONE]
      c = big_integer[:TEN]
      a.equal?(a).should be_true
      a.equal?(b).should be_true
      a.equal?(c).should be_false
    end
  end

end
