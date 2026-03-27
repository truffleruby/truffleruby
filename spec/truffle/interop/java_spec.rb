# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Java" do
  it "is defined" do
    defined?(Java).should == "constant"
  end

  guard -> { !TruffleRuby.native? } do
    describe ".type" do
      it "returns a Java class for a known primitive name" do
        Java.type("int").getName.should == "int"
      end

      it "returns a Java class for known primitive name as an array" do
        Java.type("int[]").getName.should == "[I"
      end

      it "returns a Java class for a known class name " do
        Java.type("java.math.BigInteger").getName.should == "java.math.BigInteger"
      end

      it "returns a Java class for known class name as an array" do
        Java.type("java.math.BigInteger[]").getName.should == "[Ljava.math.BigInteger;"
      end

      it "throws RuntimeError for unknown class names" do
        -> { Java.type("does.not.Exist") }.should raise_error(Polyglot::ForeignException)
      end

      it "works with symbols" do
        Java.type(:int).getName.should == "int"
      end
    end
  end
end
