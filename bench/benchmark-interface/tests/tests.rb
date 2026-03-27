#!/usr/bin/env ruby

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

test_ruby = ENV['TEST_RUBY'] || 'ruby'
version = RUBY_VERSION.split('.').first(2).map(&:to_i)
earlier_than_21 = version[0] < 2 || (version[0] == 2 && version[1] < 1) || test_ruby.include?('topaz')

regenerate = ARGV.delete('--regenerate')
resquash = ARGV.delete('--resquash')

backends = ['--simple', '--bm', '--bmbm', '--bips', '--deep']
if backends.any? { |b| ARGV.include?(b) }
  backends &= ARGV
  ARGV -= backends
end

if ARGV.empty?
  examples = Dir.glob('examples/*.rb')
  examples.delete('examples/clamp.rb')
else
  examples = ARGV
end

if File.directory?('../deep-bench')
  deep_bench = '../deep-bench'
else
  deep_bench = 'deep-bench'
end

failed = false

test_example_backend = Proc.new do |example, backend, options|
  puts "$ #{example} #{backend} #{options}"
  
  expected_file = "tests/expected/#{File.basename(example, '.rb')}-#{backend[2..-1]}.txt"
  
  if resquash
    resquashed = `cat #{expected_file} | tests/tools/squash.rb`
    File.write expected_file, resquashed
  else
    actual = `#{test_ruby} -I #{deep_bench}/lib bin/benchmark #{example} #{backend} #{options} | tee /dev/tty | tests/tools/squash.rb`
    
    if regenerate
      File.write expected_file, actual
    else
      expected = File.read expected_file
      if expected != actual
        puts 'not as expected!'
        puts 'expected:'
        puts expected
        puts 'actual:'
        puts actual
        failed = true
      end
    end
  end
end

ENV['CI'] = 'yes'

examples.each do |example|
  backends.each do |backend|
    if example == 'examples/mri.rb'
      test_example_backend.call example, backend, '' unless earlier_than_21
      
      begin
        `cp tests/rewritten/mri.rb mri-rewrite-cache.rb`
        test_example_backend.call example, backend, '--use-cache'
      ensure
        `rm -f mri-rewrite-cache.rb`
      end
    elsif backend == '--deep'
      test_example_backend.call example, backend, '--Xfirst'
    else
      test_example_backend.call example, backend, ''
    end
  end
end

abort if failed
