# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/optional/capi/spec_helper'

extension_path = load_extension("truffleruby_thread")

describe "TruffleRuby C-API Thread function" do
  before :each do
    @t = CApiTruffleRubyThreadSpecs.new
  end

  describe "rb_thread_call_without_gvl" do
    it "runs a native function with the global lock unlocked" do
      @t.rb_thread_call_without_gvl_native_function.should == Process.pid
    end

    it "is unblocked with RUBY_UBF_IO when using CPUSampler" do
      code = "require #{extension_path.dump}; CApiTruffleRubyThreadSpecs.new.rb_thread_call_without_gvl_unblock_signal"
      out = ruby_exe(code, options: '--cpusampler')
      out.should.include?('rb_thread_call_without_gvl_unblock_signal')
      out.should.include?('rb_thread_call_without_gvl')
      out.should.include?('rb_thread_call_with_gvl') # which checks guest safepoints
    end

    it "is unblocked with a custom unblock function when using CPUSampler" do
      code = "require #{extension_path.dump}; CApiTruffleRubyThreadSpecs.new.rb_thread_call_without_gvl_unblock_custom_function { |fd| Thread.new { sleep 1; IO.for_fd(fd, autoclose: false).write 'D' } }"
      out = ruby_exe(code, options: '--cpusampler')
      out.should.include?('rb_thread_call_without_gvl_unblock_custom_function')
      out.should.include?('rb_thread_call_without_gvl')
      out.should.include?('rb_thread_call_with_gvl') # which checks guest safepoints
    end
  end
end
