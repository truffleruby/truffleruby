# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe 'Critical methods which must split' do
  it 'are under 100 AST nodes' do
    code = <<-'EOF'
methods = [
  String.instance_method(:sub),
  String.instance_method(:sub!),
  String.instance_method(:gsub),
  String.instance_method(:gsub!),

  Truffle::StringOperations.method(:gsub_match_and_replace),
  Truffle::StringOperations.method(:gsub_internal_hash),
  Truffle::StringOperations.method(:gsub_internal_replacement),
  Truffle::StringOperations.method(:gsub_internal_core_check_encoding),
  Truffle::StringOperations.method(:gsub_internal_matches),
  Truffle::StringOperations.method(:gsub_new_offset),
  Truffle::StringOperations.method(:gsub_regexp_matches),
  Truffle::StringOperations.method(:gsub_string_matches),
  Truffle::StringOperations.method(:gsub_other_matches),
  Truffle::StringOperations.method(:gsub_internal_yield_matches),

  Regexp.instance_method(:=~),
  Regexp.instance_method(:match),
  Regexp.instance_method(:match?),
  Truffle::RegexpOperations.method(:match),
  Truffle::RegexpOperations.method(:match?),

  String.instance_method(:[]),
  Truffle::StringOperations.method(:subpattern),
]

methods.each do |meth|
  puts "#{Truffle::Debug.ast_size(meth)}: #{meth}"
end
EOF
    out = ruby_exe(code)
    out.lines.each do |line|
      line.should =~ /^\d\d: .+$/
    end
  end
end
