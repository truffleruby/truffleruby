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

describe "Integer#&" do
  before :each do
    @long = (1 << 48) + 1
    @mask = Primitive.integer_lower(((1 << 30) - 1))
    # Use a single call site to ensure it works even if long & long was used before.
    # (a, b) is not used to avoid promoting to long just because of the FrameSlot kind.
    @and = -> *args { args[0].send(:&, args[1]) }
    @and.call(@long, @long)
  end

  guard -> { !Truffle::Boot.get_option('chaos-data') } do
    it "returns an int for (int, int)" do
      result = (1 & 3)
      result.should == 1
      Truffle::Debug.java_class_of(result).should == 'Integer'

      result = @and.call(1, 3)
      result.should == 1
      Truffle::Debug.java_class_of(result).should == 'Integer'
    end

    it "returns an int for (long, int)" do
      Truffle::Debug.java_class_of(@long).should == 'Long'
      Truffle::Debug.java_class_of(@mask).should == 'Integer'

      result = (@long & @mask)
      result.should == 1
      Truffle::Debug.java_class_of(result).should == 'Integer'

      result = @and.call(@long, @mask)
      result.should == 1
      Truffle::Debug.java_class_of(result).should == 'Integer'
    end

    it "returns an int for (int, long)" do
      Truffle::Debug.java_class_of(@long).should == 'Long'
      Truffle::Debug.java_class_of(@mask).should == 'Integer'

      result = (@mask & @long)
      result.should == 1
      Truffle::Debug.java_class_of(result).should == 'Integer'

      result = @and.call(@mask, @long)
      result.should == 1
      Truffle::Debug.java_class_of(result).should == 'Integer'
    end
  end
end
