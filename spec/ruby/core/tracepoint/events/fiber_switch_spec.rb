require_relative '../../../spec_helper'
require_relative '../fixtures/classes'

describe 'TracePoint :fiber_switch event' do
  it 'triggers in the fiber gaining control, with the location of the call which switched fibers' do
    events = []
    line = nil
    TracePoint.new(:fiber_switch) do |tp|
      next unless TracePointSpec.target_thread?
      events << [tp.event, tp.self, tp.path, tp.lineno]
    end.enable do
      fiber = Fiber.new { Fiber.yield; :done }
      line = __LINE__ + 1
      fiber.resume
      fiber.resume
    end

    events.should == [
      [:fiber_switch, Thread.current, nil, 0], # the new fiber starts
      [:fiber_switch, Thread.current, __FILE__, line], # back in the main fiber after Fiber.yield
      [:fiber_switch, Thread.current, __FILE__, line - 2], # back in the fiber after the second resume
      [:fiber_switch, Thread.current, __FILE__, line + 1], # back in the main fiber after the fiber finished
    ]
  end
end
