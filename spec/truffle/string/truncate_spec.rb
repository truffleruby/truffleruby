# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../ruby/spec_helper'

describe "Primitive.string_truncate" do
  it "should truncate if the new byte length is shorter than the current length" do
    str = "abcdef"

    Primitive.string_truncate(str, 3)

    str.should == "abc"
  end

  it "should raise an error if the new byte length is greater than the current length" do
    -> do
      Primitive.string_truncate("abc", 10)
    end.should raise_error(ArgumentError)
  end

  it "should raise an error if the new byte length is negative" do
    -> do
      Primitive.string_truncate("abc", -1)
    end.should raise_error(ArgumentError)
  end
end
