require_relative '../../../spec_helper'
require_relative '../fixtures/classes'

describe 'TracePoint :c_return event' do
  it 'triggers for methods not implemented in Ruby, with the return value and the location of the call' do
    events = []
    tp = TracePoint.new(:c_return) do |tp|
      next unless TracePointSpec.target_thread?
      next unless tp.path == __FILE__
      events << [tp.defined_class, tp.method_id, tp.lineno, tp.self, tp.return_value, tp.binding]
    end

    line = __LINE__ + 2
    tp.enable do
      [3, 1].size
      1 + 2
    end

    events.should == [
      [Array, :size, line, [3, 1], 2, nil],
      [Integer, :+, line + 1, 1, 3, nil],
    ]
  end
end
