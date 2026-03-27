# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

guard -> { !TruffleRuby.native? } do
  describe "Java exceptions" do
    it "can be rescued with Polyglot::ForeignException" do
      integer_class = Java.type("java.lang.Integer")

      -> { integer_class.valueOf("abc") }.should raise_error(Polyglot::ForeignException)
    end

    it "can be rescued with java.lang.NumberFormatException" do
      integer_class = Java.type("java.lang.Integer")
      number_format_exception = Java.type("java.lang.NumberFormatException")

      -> { integer_class.valueOf("abc") }.should raise_error(number_format_exception)
    end

    it "can be rescued with java.lang.RuntimeException" do
      integer_class = Java.type("java.lang.Integer")
      runtime_exception = Java.type("java.lang.RuntimeException")

      -> { integer_class.valueOf("abc") }.should raise_error(runtime_exception)
    end

    it "can be rescued with java.lang.Throwable" do
      integer_class = Java.type("java.lang.Integer")
      throwable = Java.type("java.lang.Throwable")

      -> { integer_class.valueOf("abc") }.should raise_error(throwable)
    end
  end
end
