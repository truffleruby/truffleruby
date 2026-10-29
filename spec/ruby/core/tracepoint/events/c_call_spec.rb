require_relative '../../../spec_helper'
require_relative '../fixtures/classes'

describe 'TracePoint :c_call event' do
  def trace_point_spec_ruby_method
  end
  ruby_method_line = __LINE__ - 2

  it 'triggers for methods not implemented in Ruby, while :call triggers for methods implemented in Ruby' do
    events = []
    tp = TracePoint.new(:call, :c_call) do |tp|
      next unless TracePointSpec.target_thread?
      next unless tp.path == __FILE__
      events << [tp.event, tp.defined_class, tp.method_id, tp.lineno, tp.self, tp.binding]
    end

    line = __LINE__ + 2
    tp.enable do
      [3, 1].size
      1 + 2
      trace_point_spec_ruby_method
    end

    events.size.should == 3
    events[0].should == [:c_call, Array, :size, line, [3, 1], nil]
    events[1].should == [:c_call, Integer, :+, line + 1, 1, nil]

    event, defined_class, method_id, lineno, self_value, binding = events[2]
    event.should == :call
    defined_class.should == singleton_class
    method_id.should == :trace_point_spec_ruby_method
    lineno.should == ruby_method_line
    self_value.should equal(self)
    binding.should.is_a?(Binding)
  end
end
