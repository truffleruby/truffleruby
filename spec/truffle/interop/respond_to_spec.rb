# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop.foreign_respond_to?" do
  describe "for :to_a" do
    it "and a Java array returns true" do
      Truffle::Interop.java_array(1, 2, 3).should.respond_to?(:to_a)
    end
  end

  describe "for :to_ary" do
    it "and a Java array returns true" do
      Truffle::Interop.java_array(1, 2, 3).should.respond_to?(:to_ary)
    end
  end

  describe "for :to_s" do
    it "and a Java class returns true" do
      Truffle::Debug.java_class.should.respond_to?(:to_s)
    end

    it "and a Java object returns true" do
      Truffle::Debug.java_object.should.respond_to?(:to_s)
    end
  end

  describe "for :to_str" do
    it "and a Java class returns false" do
      Truffle::Debug.java_class.should_not.respond_to?(:to_str)
    end

    it "and a Java object returns false" do
      Truffle::Debug.java_object.should_not.respond_to?(:to_str)
    end

    it "and a boxed string returns true" do
      Truffle::Debug.foreign_string('test').should.respond_to?(:to_str)
    end

    it "and a Java array returns false" do
      Truffle::Interop.java_array(1, 2, 3).should_not.respond_to?(:to_str)
    end
  end

  describe "for :is_a?" do
    it "and a Java class returns true" do
      Truffle::Debug.java_class.should.respond_to?(:is_a?)
    end

    it "and a Java object returns true" do
      Truffle::Debug.java_object.should.respond_to?(:is_a?)
    end

    it "and a Java array returns true" do
      Truffle::Interop.java_array(1, 2, 3).should.respond_to?(:is_a?)
    end
  end
end
