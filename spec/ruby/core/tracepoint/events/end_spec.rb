require_relative '../../../spec_helper'
require_relative '../fixtures/classes'

describe 'TracePoint :end event' do
  before do
    ScratchPad.record []
  end

  it 'triggers at the end of a class definition' do
    TracePoint.new(:end) do |tp|
      next unless TracePointSpec.target_thread?
      ScratchPad.record [tp.event, tp.self, tp.lineno, tp.path]
    end.enable do
      class TracePointSpec::ClassDefined
      end
    end

    ScratchPad.recorded.should == [:end, TracePointSpec::ClassDefined, __LINE__ - 3, __FILE__]
  end

  it 'triggers at the end of a module definition' do
    TracePoint.new(:end) do |tp|
      next unless TracePointSpec.target_thread?
      ScratchPad.record [tp.event, tp.self, tp.lineno, tp.path]
    end.enable do
      module TracePointSpec::ModuleDefined
      end
    end

    ScratchPad.recorded.should == [:end, TracePointSpec::ModuleDefined, __LINE__ - 3, __FILE__]
  end
end
