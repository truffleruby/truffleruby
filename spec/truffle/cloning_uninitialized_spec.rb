# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "RubyNode#cloneUninitialized() for nodes implementing AssignableNode interface" do
  # The Module#define_method with block argument triggers cloning of the proc and converting it into a lambda
  it "works for local variable assignment" do
    Module.new.define_method("foo") do
    rescue => e # rubocop:disable Lint/SuppressedException, Lint/UselessAssignment
    end

    1.should == 1
  end

  it "works for local variable assignment when it is already declared in an outer scope" do
    e = nil
    Module.new.define_method("foo") do
    rescue => e # rubocop:disable Lint/SuppressedException
    end

    e.should == nil
  end

  it "works for instance variable assignment" do
    Module.new.define_method("foo") do
    rescue => @e # rubocop:disable Lint/SuppressedException
    end

    1.should == 1
  end

  it "works for class variable assignment" do
    Module.new.define_method("foo") do
    rescue => @@e # rubocop:disable Lint/SuppressedException
    end

    1.should == 1
  end

  it "works for global variable assignment" do
    Module.new.define_method("foo") do
    rescue => $clonning_uninitialized_spec # rubocop:disable Lint/SuppressedException
    end

    $clonning_uninitialized_spec.should == nil
  end

  it "works for a constant assignment" do
    m = Module.new
    m.define_method("foo") do
    rescue => m::E # rubocop:disable Lint/SuppressedException
    end

    1.should == 1
  end

  it "works for a multi-assignment" do
    a = nil
    Module.new.define_method("foo") do
      a, b, c = [] # rubocop:disable Lint/UselessAssignment
    end

    a.should == nil
  end
end
