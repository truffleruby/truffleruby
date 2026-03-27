# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

guard -> { Truffle::Interop.polyglot_bindings_access? } do
  describe "Truffle::Interop.export" do
    it "exports an object" do
      object = Object.new
      Truffle::Interop.export :exports_an_object, object
      Truffle::Interop.import(:exports_an_object).should.equal? object
    end

    it "exports a primitive boolean" do
      Truffle::Interop.export :exports_a_primitive_number, true
      Truffle::Interop.import(:exports_a_primitive_number).should.equal? true
    end

    it "exports a primitive number" do
      Truffle::Interop.export :exports_a_primitive_number, 14
      Truffle::Interop.import(:exports_a_primitive_number).should.equal? 14
    end

    it "exports a string" do
      ruby_string = 'hello'
      Truffle::Interop.export :exports_a_string, ruby_string
      Truffle::Interop.import(:exports_a_string).should.equal?(ruby_string)
    end

    it "exports a symbol" do
      Truffle::Interop.export :exports_a_symbol, :hello
      Truffle::Interop.import(:exports_a_symbol).should.equal? :hello
    end

    it "exports a foreign object" do
      foreign_object = Truffle::Debug.foreign_object
      Truffle::Interop.export :exports_a_foreign_object, foreign_object
      Truffle::Interop.import(:exports_a_foreign_object).should.equal?(foreign_object)
    end

    it "exports a Java string" do
      java_string = Truffle::Interop.to_java_string('hello')
      Truffle::Interop.export :exports_a_java_string, java_string
      Truffle::Interop.import(:exports_a_java_string).should.equal?(java_string)
      java_string.should == 'hello'
    end

    it "does not convert to Java when exporting a Ruby string" do
      ruby_string = 'hello'
      Truffle::Interop.export :exports_a_string_with_conversion, ruby_string
      imported = Truffle::Interop.import(:exports_a_string_with_conversion)
      Truffle::Interop.should_not.java_string?(imported)
      imported.should.equal?(ruby_string)
    end

    it "can be used with a string name" do
      Truffle::Interop.export 'string_name', 'hello'
      Truffle::Interop.import('string_name').should == 'hello'
    end

    it "can be used with a symbol name" do
      Truffle::Interop.export :symbol_name, 'hello'
      Truffle::Interop.import(:symbol_name).should == 'hello'
    end

    it "can be used with a mix of string and symbol names" do
      Truffle::Interop.export :mixed_name, 'hello'
      Truffle::Interop.import('mixed_name').should == 'hello'
    end

    it "returns the original exported value" do
      string = 'hello'
      Truffle::Interop.export(:returns_original_value, string).should.equal?(string)
    end
  end
end
