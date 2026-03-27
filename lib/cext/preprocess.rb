# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../truffle/truffle/cext_preprocessor'

require 'stringio'

file_name = ARGV.first
original_content = File.read(file_name)
output = Truffle::CExt::Preprocessor.patch(file_name, original_content, Dir.pwd)

if ENV['PREPROCESS_DEBUG'] && original_content != output
  patched_file_name = "#{File.dirname file_name}/.#{File.basename file_name, '.*'}.patched#{File.extname file_name}"
  File.write patched_file_name, output
  $stderr.print `git diff --no-index --color -- #{file_name} #{patched_file_name}`
  file_name = patched_file_name
end

expanded_path = File.expand_path(file_name)
$stdout.puts "#line 1 \"#{expanded_path}\""
$stdout.puts output
