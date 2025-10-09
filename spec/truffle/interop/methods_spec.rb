# truffleruby_primitives: true

# Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "Truffle::Interop" do
  # Run locally and in TruffleRuby's CI but not in GraalVM's CI to not prevent adding new interop messages
  guard -> {
    ENV['TRUFFLERUBY_ALL_INTEROP_LIBRARY_METHODS_SPEC'] != 'false'
  } do
    it "has a method for each InteropLibrary message" do
      all_methods = Primitive.interop_library_all_methods
      expected = all_methods.map do |name|
        name = name.gsub(/([a-z])([A-Z])/) { "#{$1}_#{$2.downcase}" }
        if name.start_with?('is_', 'has_', 'fits_')
          name += '?'
        end
        if name.start_with?('is_')
          name = name[3..-1]
        elsif name.start_with?('get_')
          name = name[4..-1]
        end
        name.to_sym
      end.sort

      actual = Truffle::Interop.methods.sort

      # pp expected
      # pp actual
      (expected - actual).should == []
    end
  end
end
