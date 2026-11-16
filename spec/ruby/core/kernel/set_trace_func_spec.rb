require_relative '../../spec_helper'
require_relative 'fixtures/classes'

describe "Kernel#set_trace_func" do
  it "is a private method" do
    Kernel.private_instance_methods(false).should.include?(:set_trace_func)
  end

  it "calls the proc with the event name, path, line, method name, binding and class for each event" do
    object = Object.new
    def object.add(a, b)
      a + b
    end
    def_line = __LINE__ - 3

    events = []
    trace_proc = proc { |event, file, line, id, binding, klass|
      if file == __FILE__ && id != :set_trace_func
        events << [event, file, line, id, binding.class, klass]
      end
    }
    call_line = __LINE__ + 2
    set_trace_func trace_proc
    object.add(1, 2)
    set_trace_func nil

    events.should == [
      ["line", __FILE__, call_line, nil, Binding, nil],
      ["call", __FILE__, def_line, :add, Binding, object],
      ["line", __FILE__, def_line + 1, :add, Binding, object],
      ["c-call", __FILE__, def_line + 1, :+, NilClass, Integer],
      ["c-return", __FILE__, def_line + 1, :+, NilClass, Integer],
      ["return", __FILE__, def_line + 2, :add, Binding, object],
      ["line", __FILE__, call_line + 1, nil, Binding, nil],
    ]
  end
end

describe "Kernel.set_trace_func" do
  it "is a public method" do
    Kernel.public_methods(false).should.include?(:set_trace_func)
  end
end
