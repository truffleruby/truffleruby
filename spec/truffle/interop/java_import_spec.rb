# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

guard -> { !TruffleRuby.native? } do
  describe "Java.import" do
    it "imports a class under the enclosing module" do
      module TruffleJavaImportSpecs1
        Java.import 'java.math.BigInteger'
        BigInteger.new('1234').toString.should == "1234"
      end

      TruffleJavaImportSpecs1.should.const_defined?('BigInteger', false)
      Object.should_not.const_defined?('BigInteger')
    end

    it "returns the imported class" do
      module TruffleJavaImportSpecs2
        Java.import('java.math.BigInteger')[:class].getName.should == "java.math.BigInteger"
      end
    end

    it "can import classes twice" do
      module TruffleJavaImportSpecs3
        Java.import 'java.math.BigInteger'
        Java.import 'java.math.BigInteger'
        BigInteger.new('1234').toString.should == "1234"
      end
    end

    it "raises an error if the constant is already defined" do
      module TruffleJavaImportSpecs4
        BigInteger = '14'
        -> {
          Java.import 'java.math.BigInteger'
        }.should raise_error(NameError, "constant BigInteger already set")
      end
    end
  end
end
