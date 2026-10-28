require_relative '../../../spec_helper'
require_relative '../fixtures/classes'

describe 'TracePoint :thread_begin event' do
  it 'triggers in a new thread before running its block, without a location or binding' do
    recorded = nil
    trace = TracePoint.new(:thread_begin) do |tp|
      next if Thread.current == Thread.main
      recorded = [tp.event, tp.self == Thread.current, tp.path, tp.lineno, tp.binding, tp.method_id]
    end

    # TracePoint#enable with a block only enables the TracePoint for the current thread
    trace.enable
    begin
      Thread.new {}.join
    ensure
      trace.disable
    end

    recorded.should == [:thread_begin, true, nil, 0, nil, nil]
  end
end
