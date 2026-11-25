# Copyright (c) 2026 TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

require_relative '../../ruby/optional/capi/spec_helper'

extension_path = load_extension("truffleruby_mark")

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

  [:rb_gc_mark_movable, :rb_gc_mark_maybe, :rb_gc_mark_locations].each do |mark_function|
    it "keeps every object it marks with #{mark_function}() alive" do
      object = @s.new_marked_values("value", nil, mark_function)
      3.times { GC.start }
      @s.marked_values(object).should == expected("value")
    end
  end

  it "keeps the objects it marks alive when another mark function runs while it is running" do
    outer = @s.new_marked_values("outer", @s.new_marked_values("inner"))
    @s.access_data(outer)
    3.times { GC.start }
    @s.should.nested_marking_observed?
    @s.marked_values(outer).should == expected("outer")
  end

  it "can raise an exception, after which the objects marked before are still alive and mark functions still run" do
    object = @s.new_marked_values("value")
    -> {
      @s.access_data_raising_in_mark_function(object)
    }.should.raise(RuntimeError, "raised by the mark function")
    3.times { GC.start }
    @s.marked_values(object).should == expected("value")

    other = @s.new_marked_values("other")
    @s.access_data(object)
    3.times { GC.start }
    @s.marked_values(object).should == expected("value")
    @s.marked_values(other).should == expected("other")
  end

  it "propagates an exception raised by a mark function which runs while it is running" do
    inner = @s.new_marked_values("inner")
    outer = @s.new_marked_values("outer", inner)
    -> {
      @s.access_data_raising_in_mark_function(outer)
    }.should.raise(RuntimeError, "raised by the mark function")

    @s.access_data(outer)
    3.times { GC.start }
    @s.marked_values(outer).should == expected("outer")
    @s.marked_values(inner).should == expected("inner")
  end
end

describe "rb_gc_mark() and its variants" do
  it "do nothing when called outside of a mark function" do
    s = CApiTruffleRubyMarkSpecs.new
    s.mark_outside_of_mark_function("value").should == true
  end

  it "log a single warning with --cexts-log-warnings when called outside of a mark function" do
    code = 'require ARGV[0]; s = CApiTruffleRubyMarkSpecs.new; 2.times { s.mark_outside_of_mark_function("value") }'
    out = ruby_exe(code, options: '--experimental-options --cexts-log-warnings', args: "#{extension_path} 2>&1")
    out.scan(/rb_gc_mark\(\) was called outside of a mark function/).size.should == 1

    out = ruby_exe(code, args: "#{extension_path} 2>&1")
    out.should_not.include?("rb_gc_mark()")
  end
end
