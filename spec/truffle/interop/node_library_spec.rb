# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.


require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

describe "Truffle::Interop.scope" do

  # rubocop:disable Lint/UselessAssignment
  # rubocop:disable Lint/UnusedBlockArgument

  it "returns the members" do
    b = :test
    def get_scope()
      a = 42
      Primitive.current_scope
    end
    scope = get_scope()
    Truffle::Interop.read_member(scope, "a").should == 42
    Truffle::Interop.members(scope).should == ["a", "self"]
  end

  it "has a parent scope with the correct members" do
    def get_scopes()
      scope = nil
      a = :a
      ["one"].each do |value|
        b = :b
        ["two"].each do |inner|
          c = :c
          scope = Primitive.current_scope
        end
      end
      scope
    end
    scope = get_scopes()
    Truffle::Interop.members(scope).should == ["inner", "c", "self", "value", "b", "self", "scope", "a", "self"]
    scope["inner"].should == "two"
    scope["c"].should == :c
    scope["value"].should == "one"
    scope["b"].should == :b
    scope["a"].should == :a
    parent_scope = Truffle::Interop.scope_parent(scope)
    Truffle::Interop.members(parent_scope).should == ["value", "b", "self", "scope", "a", "self"]
    parent_scope["b"].should == :b
    -> { parent_scope["missing"] }.should raise_error(NameError)
    parent_parent_scope = Truffle::Interop.scope_parent(parent_scope)
    Truffle::Interop.members(parent_parent_scope).should == ["scope", "a", "self"]
  end

  it "allows reading and writing scope members" do
    def get_scopes()
      scope = nil
      a = :a
      ["one"].each do |value|
        b = :b
        ["two"].each do |inner|
          c = :c
          scope = Primitive.current_scope
        end
      end
      scope
    end
    scope = get_scopes()
    scope["inner"].should == "two"
    scope["inner"] = "three"
    scope["inner"].should == "three"

    scope["c"].should == :c
    scope["c"] = :d
    scope["c"].should == :d

    scope["a"].should == :a
    scope["a"] = :b
    scope["a"].should == :b

    -> { scope["missing"] = "missing" }.should raise_error(NameError)
  end


  # rubocop:enable Lint/UselessAssignment
  # rubocop:enable Lint/UnusedBlockArgument

end
