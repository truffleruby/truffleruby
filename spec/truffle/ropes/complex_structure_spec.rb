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

describe "Truffle Rope complex structure" do

  [
      [(('abcd'*3)[1..-1]+('ABCD')), 'bcdabcdabcdABCD'],
      [(('abcd'*3)[1..-2]+('ABCD')), 'bcdabcdabcABCD'],
      [(('abcd'*3)[1..-3]+('ABCD')), 'bcdabcdabABCD'],
      [(('abcd'*3)[1..-4]+('ABCD')), 'bcdabcdaABCD'],
      [(('abcd'*3)[1..-5]+('ABCD')), 'bcdabcdABCD'],
      [(('ab'*4)+'0123456789')[1..-2]+'cd', 'bababab012345678cd']
  ].each_with_index do |(a, b), i|
    it format('%d: %s', i, b) do
      a.hash.should == b.hash
      a.should == b
    end
  end

end
