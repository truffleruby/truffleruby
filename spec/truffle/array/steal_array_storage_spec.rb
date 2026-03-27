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

describe ":steal_array_storage primitive" do
  def storage(ary)
    Truffle::Debug.array_storage(ary)
  end

  guard -> { !Truffle::Boot.get_option('chaos-data') } do
    it "should no-op when called on itself" do
      array = %i[first second third]
      Primitive.steal_array_storage(array, array)

      storage(array).should == "Object[]"
      array.should == %i[first second third]
    end

    it "should take ownership of the store" do
      array = %i[first second third]
      other = [1, 2, 3, 4, 5]
      Primitive.steal_array_storage(array, other)

      storage(array).should == "int[]"
      array.should == [1, 2, 3, 4, 5]

      storage(other).should == "empty"
      other.should.empty?
    end
  end
end
