# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../pe/pe_harness'

def foo
  var = 14
  var * 2
end
LINE_TO_MODIFY = __LINE__ - 2

set_trace_func proc { |event, file, line, id, binding, classname|
  if event == 'line' && file == __FILE__ && line == LINE_TO_MODIFY
    binding.local_variable_set(:var, 100)
  end
}

create_test_pe_code_method('foo')
begin
  while true
    x = test_pe_code
    raise 'value not correct' unless x == 200
  end
rescue Truffle::GraalError => e
  if e.message.include? 'Primitive.assert_not_compiled'
    puts 'STF optimising'
    exit
  elsif e.message.include? 'Primitive.assert_compilation_constant'
    abort 'STF not optimising'
  else
    raise e
  end
end

abort 'should not reach here'
