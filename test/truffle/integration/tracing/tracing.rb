# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

$trace = []

$trace_proc = proc { |*args|
  binding = args[4]
  # Capture the current values of variables at this point in time.
  # The binding is nil for c-call and c-return events.
  unless binding.nil?
    args[4] = Hash[binding.local_variables.sort.map { |v|
      [v, binding.local_variable_get(v)]
    }]
  end
  $trace << args
}

def check(file)
  expected = nil

  File.open('test/truffle/integration/tracing/' + file) do |f|
    expected = f.each_line.map { |line| eval(line) }
  end

  # Ignore events from the core library, such as from the implementation of set_trace_func
  actual = $trace.reject { |event, file, *| file.start_with?('<internal:core>') }

  actual = actual.map do |event, file, line, id, binding, classname, *extra|
    raise unless extra.empty?
    file = '/' + File.basename(file)
    [event, file, line, id, binding, classname]
  end

  # Remove extra :set_trace_func calls/returns
  actual = actual.reject { |event, file, line, id, binding, classname| id == :set_trace_func }

  empty_binding = {}

  while actual.size < expected.size
    actual.push ['missing', 'missing', :missing, :missing, empty_binding, :missing]
  end

  while expected.size < actual.size
    expected.push ['missing', 'missing', :missing, :missing, empty_binding, :missing]
  end

  success = true
  trace_event = 1

  expected.zip(actual).each do |e, a|
    unless a == e
      puts "Event #{trace_event}\n    Expected #{e.inspect}\n    actually #{a.inspect}"
      success = false
    end
    trace_event += 1
  end

  unless success
    puts
    puts 'Expected:'
    expected.each { |e| p e  }
    puts
    puts 'Actual:'
    actual.each { |e| p e  }
    exit 1
  end
end
