# Copyright (c) 2026 TruffleRuby contributors.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/optional/capi/spec_helper'

load_extension("truffleruby_mark")

describe "A typed data mark function" do
  before :each do
    @s = CApiTruffleRubyMarkSpecs.new
  end

  def expected(prefix)
    200.times.map { |i| "#{prefix} #{i}" }
  end

  it "keeps every object it marks with rb_gc_mark() alive" do
    object = @s.new_marked_values("value")
    3.times { GC.start }
    @s.marked_values(object).should == expected("value")
  end

  it "keeps the objects it marks alive when another mark function runs while it is running" do
    outer = @s.new_marked_values("outer", @s.new_marked_values("inner"))
    @s.access_data(outer)
    3.times { GC.start }
    @s.should.nested_marking_observed?
    @s.marked_values(outer).should == expected("outer")
  end
end
