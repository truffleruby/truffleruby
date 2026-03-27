# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

guard -> { Truffle::Interop.polyglot_bindings_access? } do
  describe "Truffle::Interop.import" do

    it "imports an object" do
      object = Object.new
      Truffle::Interop.export :imports_an_object, object
      Truffle::Interop.import(:imports_an_object).should == object
    end

    it "raises NameError if not found" do
      -> {
        Truffle::Interop.import(:not_registered_export_test)
      }.should raise_error(NameError, "import 'not_registered_export_test' not found")
    end

  end
end
