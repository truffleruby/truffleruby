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

describe "A substring" do
  describe "of a UTF-8 valid SubstringRope" do
    it "correctly computes the index and consider offset as a byte offset" do
      complex = ("ééé" + "ascii)")[2..-1]
      complex[-1,1].should == ")"
      complex.end_with?(")").should == true

      str = "aétèbcd)"
      complex = (str[0...4] + str[4..-1])[3..-1]
      complex[-1,1].should == ")"
      complex.end_with?(")").should == true
    end
  end
end
