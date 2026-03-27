# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Truffle::VersionedArray" do
  it "redefines all Array and Enumerable methods" do
    expected_methods = ruby_exe("puts Array.public_instance_methods(false) + Enumerable.public_instance_methods(false)")
    expected_methods = expected_methods.lines(chomp: true).map { |line| line.to_sym }.uniq!.sort!
    versioned_methods = Truffle::VersionedArray.public_instance_methods(false).sort!
    versioned_methods.delete(:version)

    if versioned_methods == expected_methods
      versioned_methods.should == versioned_methods
    else
      # if the fast check fails, do a slower check to determine why
      expected_methods.each do |m|
        versioned_methods.should include(m)
      end
      versioned_methods.each do |m|
        expected_methods.should include(m)
      end
    end
  end
end
