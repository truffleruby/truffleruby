# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
#

describe "NodeLibrary top scope" do
  it "returns the members" do
    top_scope = Primitive.top_scope
    Truffle::Boot::INTERACTIVE_BINDING.eval('top_scope_spec_var = :top_scope_spec')

    scope_members = Truffle::Interop.members(top_scope)
    scope_members.should.include?('top_scope_spec_var')
    scope_members.should.include?('$stdout')
    scope_members.should.include?('is_a?')

    scope_members.index('top_scope_spec_var').should < scope_members.index('$stdout')
    scope_members.index('$stdout').should < scope_members.index('is_a?')

    top_scope["top_scope_spec_var"].should == :top_scope_spec
    top_scope["$stdout"].should == $stdout
    top_scope["is_a?"].should be_kind_of(Method)
  end

  it "is separate from TOPLEVEL_BINDING" do
    TOPLEVEL_BINDING.eval('top_scope_spec_top_binding_var = nil')

    TOPLEVEL_BINDING.local_variables.should.include?(:top_scope_spec_top_binding_var)
    Truffle::Boot::INTERACTIVE_BINDING.local_variables.should_not.include?(:top_scope_spec_top_binding_var)
    Truffle::Interop.members(Primitive.top_scope).should_not.include?('top_scope_spec_top_binding_var')
  end
end
