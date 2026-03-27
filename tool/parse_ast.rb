# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Based on bin/ast from JRuby

abort "Usage: jt ruby #{__FILE__} FILE or 'CODE'" unless ARGV.size == 1
if File.exist?(ARGV[0])
  code = File.read(ARGV[0])
else
  code = ARGV[0]
end

def indexes(string, lindex, rindex)
  lindex = string.index("(", lindex) if lindex
  rindex = string.index(")", rindex) if rindex
  [lindex, rindex]
end

def indent(string)
  depth = -1

  lindex, rindex = indexes(string, 0, 0)

  while lindex || rindex
    if lindex && lindex < rindex
      depth += 1
      string[lindex, 1] = "\n#{'  ' * depth}"
    else
      depth -= 1
      string[rindex, 1] = "\n"
    end

    lindex, rindex = indexes(string, lindex, rindex)
  end
  string.gsub(/,\s*$/, '').squeeze("\n")
end

puts "Source:"
puts code
puts

if RUBY_ENGINE == "jruby"
  print "AST:"
  require 'jruby'
  ast_to_string = JRuby.parse(code).to_string
else
  puts "AST:"
  ast_to_string = Truffle::Debug.parse_ast(code)
end
puts indent(ast_to_string)
