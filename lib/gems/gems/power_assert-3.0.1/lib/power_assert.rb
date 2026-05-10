# power_assert.rb
#
# Copyright (C) 2014 Kazuki Tsujimoto

begin
  captured = false
  target_thread = Thread.current
  TracePoint.new(:return, :c_return) do |tp|
    next unless Thread.current == target_thread
    captured = true
    unless tp.return_value and tp.callee_id
      raise ''
    end
  end.enable { __id__ }
  raise '' unless captured
rescue
  raise LoadError, 'Fully compatible TracePoint API required'
end

require 'power_assert/context'
require 'power_assert/configuration'
require 'power_assert/version'

module PowerAssert
  POWER_ASSERT_LIB_DIR = File.dirname(caller_locations(1, 1).first.path)
  INTERNAL_LIB_DIRS = {PowerAssert => POWER_ASSERT_LIB_DIR}
  private_constant :POWER_ASSERT_LIB_DIR, :INTERNAL_LIB_DIRS

  class << self
    def start(assertion_proc_or_source, assertion_method: nil, source_binding: TOPLEVEL_BINDING)
      clear_global_method_cache
      yield Context.new(assertion_proc_or_source, assertion_method, source_binding)
    end

    def app_caller_locations
      caller_locations.drop_while {|i| internal_file?(i.path) }.take_while {|i| ! internal_file?(i.path) }
    end

    def app_context?
      top_frame = caller_locations.drop_while {|i| i.path.start_with?(POWER_ASSERT_LIB_DIR) }.first
      top_frame and ! internal_file?(top_frame.path)
    end

    private

    def internal_file?(file)
      INTERNAL_LIB_DIRS.find do |_, dir|
        file.start_with?(dir)
      end
    end

    CLEAR_CACHE_ISEQ = RubyVM::InstructionSequence.compile('using PowerAssert.const_get(:Empty)')
    private_constant :CLEAR_CACHE_ISEQ

    def clear_global_method_cache
      CLEAR_CACHE_ISEQ.eval
    end
  end

  module Empty
  end
  private_constant :Empty
end
