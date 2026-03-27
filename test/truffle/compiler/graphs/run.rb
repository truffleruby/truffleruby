# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'json'

$tests = 0
$failures = 0

def test(program, shape_expected)
  $tests += 1
  command = "bin/jt -q graph --json --describe #{program}"
  output = `#{command}`
  unless $?.success?
    abort "`#{command}` failed (#{$?}).\nOutput:\n#{output}\n"
  end
  last_line = output.lines.last.strip

  begin
    decoded = JSON.parse(last_line, symbolize_names: true)
  rescue JSON::ParserError => e
    abort "ERROR: Could not parse JSON from command `#{command}` (#{e}).\n" \
      "JSON line:\n#{last_line}\nFull output:\n#{output}\n"
  end

  shape_got = decoded

  mismatched_features = shape_expected.keys - shape_got.keys

  mismatched_features += shape_got.keys.select do |actual_key|
    actual_value = shape_got[actual_key]

    if shape_expected.key?(actual_key)
      actual_value == shape_expected[actual_key] ? nil : actual_key
    elsif actual_value == false or actual_value.is_a?(Integer)
      # false means the feature is not present, which is expected.
      # integer values are ignored unless set in the shape_expected.
      nil
    elsif actual_key == :node_counts
      nil
    else
      # The actual shape has features that the test did not expect.
      actual_key
    end
  end

  if mismatched_features.empty?
    puts "#{program}: ✓"
  else
    puts "#{program}: mismatched features: #{mismatched_features} differ; (expected #{shape_expected}, got #{shape_got})"
    $failures += 1
  end
end

test 'test/truffle/compiler/graphs/args-req.rb', { linear: true }
test 'test/truffle/compiler/graphs/args-opt-set.rb', { linear: true }
test 'test/truffle/compiler/graphs/args-opt-unset.rb', { linear: true}
test 'test/truffle/compiler/graphs/args-rest.rb', { linear: true }

puts "#{$tests} tests, #{$failures} failures"
exit 1 if $failures > 0
