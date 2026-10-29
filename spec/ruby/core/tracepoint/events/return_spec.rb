require_relative '../../../spec_helper'
require_relative '../fixtures/classes'

describe 'TracePoint :return event' do
  def trace_point_spec_implicit_return
    1
  end
  implicit_return_end_line = __LINE__ - 1

  def trace_point_spec_explicit_return
    i = 0
    while true
      return 2 if i == 1
      i += 1
    end
  end
  explicit_return_line = __LINE__ - 4

  def trace_point_spec_raise
    raise 'boom'
  end

  def trace_point_spec_yield
    yield
  end

  def trace_point_spec_non_local_return
    [1].each { |x| return x }
  end
  non_local_return_line = __LINE__ - 2

  it 'triggers with the return value, or nil when the method exits by an exception, throw or break' do
    events = []
    tp = TracePoint.new(:return) do |tp|
      next unless TracePointSpec.target_thread?
      next unless tp.path == __FILE__
      events << [tp.method_id, tp.return_value, tp.lineno, tp.binding.is_a?(Binding)]
    end

    tp.enable do
      trace_point_spec_implicit_return
      trace_point_spec_explicit_return
      -> { trace_point_spec_raise }.should.raise(RuntimeError, 'boom')
      catch(:tag) { trace_point_spec_yield { throw :tag } }
      trace_point_spec_yield { break }
      trace_point_spec_non_local_return
    end

    events.map { |method_id, *| method_id }.should == [
      :trace_point_spec_implicit_return,
      :trace_point_spec_explicit_return,
      :trace_point_spec_raise,
      :trace_point_spec_yield,
      :trace_point_spec_yield,
      :trace_point_spec_non_local_return,
    ]
    events.map { |_, return_value, _, binding| [return_value, binding] }.should == [
      [1, true],
      [2, true],
      [nil, true],
      [nil, true],
      [nil, true],
      [1, true],
    ]

    # The line is that of the return expression, or of the end of the method for an implicit return.
    # It is implementation-specific when the method exits by an exception, throw or break.
    events[0][2].should == implicit_return_end_line
    events[1][2].should == explicit_return_line
    events[5][2].should == non_local_return_line
  end

  it 'does not support TracePoint#return_value for other events' do
    -> {
      TracePoint.new(:call) { |tp|
        next unless TracePointSpec.target_thread?
        tp.return_value if tp.path == __FILE__
      }.enable { trace_point_spec_implicit_return }
    }.should.raise(RuntimeError, 'not supported by this event')
  end
end
