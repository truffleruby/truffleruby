#!/usr/bin/env ruby
# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'json'

result = JSON.parse(File.read(ARGV[0]))
wait = ARGV[1] == '--wait'

failures = []

known_failures = [
  # OOM in flattenBytes
  ["server", "graal-core", "jruby", "truffle", "micro", "micro/file/read.rb:core-read-gigabyte"],
  ["server", "graal-enterprise", "jruby", "truffle", "micro", "micro/file/read.rb:core-read-gigabyte"],
  ["server", "graal-enterprise-no-om", "jruby", "truffle", "micro", "micro/file/read.rb:core-read-gigabyte"],

  # C-exts
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-r"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-g"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-b"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-a"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-color-compose-quick"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-bilinear"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-nearest-neighbor"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-steps-residues"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-canvas-resampling-steps"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-decode-png-image-pass"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-encode-png-image-pass-to-stream"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-operations-compose"],
  ["server", "graal-core", "jruby", "truffle-cexts", "chunky", "chunky-operations-replace"],

  # JRuby
  ["server", "default", "jruby", "indy", "micro", "micro/file/read.rb:core-read-gigabyte"]
]

if File.exist?('failures')
  failures = File.read('failures').split("\n").map { |failure| eval(failure) }
else
  failures = []
end

(result['queries'] || []).any? do |q|
  if q['error'] == 'failed'
    failures.push [q['host-vm'], q['host-vm-config'], q['guest-vm'], q['guest-vm-config'], q['bench-suite'], q['benchmark']]
  end
end

known_failures.each do |known_failure|
  if failures.delete(known_failure)
    STDERR.puts "#{known_failure.inspect} failed, but we know about that"
  end
end

if wait
  if !failures.empty?
    STDERR.puts 'waiting to return failure...'
    File.write('failures', failures.map(&:inspect).join("\n"))
  end
else
  if !failures.empty? || File.exist?('failures')
    STDERR.puts 'these failed:'
    failures.each do |failure|
      STDERR.puts failure.inspect
    end
    exit 1
  end
end
