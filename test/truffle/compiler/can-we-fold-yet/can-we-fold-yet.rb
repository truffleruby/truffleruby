# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Use --experimental-options --compiler.IterativePartialEscape --engine.MultiTier=false

require 'readline'

require_relative '../pe/pe_harness'

puts 'Can TruffleRuby constant fold yet?'

loop do
  if STDIN.tty?
    code = Readline.readline('> ', true)
  else
    # Workaround for https://github.com/ruby/reline/issues/886
    print '> '
    code = STDIN.gets
    puts code
  end
  break unless code # EOF

  test_thread = Thread.new do
    begin
      create_test_pe_code_method(code)
      while true
        value = test_pe_code
      end
    rescue Truffle::GraalError => e
      if e.message.include? 'Primitive.assert_not_compiled'
        puts "Yes! Truffle can constant fold this to #{value.inspect}"
      elsif e.message.include? 'Primitive.assert_compilation_constant'
        puts "No :( Truffle can't constant fold that"
      else
        puts 'There was an error executing that :('
      end
    end
  end

  unless test_thread.join(10)
    puts 'That timed out :( either it takes too long to execute or to compile'
  end
end
