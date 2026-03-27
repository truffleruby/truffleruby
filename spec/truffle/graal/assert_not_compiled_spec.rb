# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Primitive.assert_not_compiled" do

  it "raises a RuntimeError when called dynamically" do
    -> { tp = Primitive; tp.assert_not_compiled }.should raise_error(NameError, /uninitialized constant/)
  end

  guard -> { !TruffleRuby.jit? } do
    it "returns nil" do
      Primitive.assert_not_compiled.should be_nil
    end
  end

end
