# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

abort "Usage: jt ruby #{__FILE__} FILE or 'CODE'" unless ARGV.size == 1
if File.exist?(ARGV[0])
  code = File.read(ARGV[0])
else
  code = ARGV[0]
end

puts Truffle::Debug.parse_and_dump_truffle_ast(code, "org.truffleruby.language.RubyTopLevelRootNode", 0, true).strip
