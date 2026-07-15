# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'benchmark/ips'
require 'ffi'

ARGUMENT = -14
EXPECTED = 14

module LabsPOSIX
end

Truffle::POSIX.attach_function_eagerly(
  :labs,
  [:long],
  :long,
  Truffle::POSIX::LIBC,
  false,
  :labs,
  LabsPOSIX)

module LabsFFI
  extend FFI::Library

  ffi_lib FFI::Library::LIBC
  attach_function :labs, [:long], :long
end

BENCHMARKS = {}
SKIPPED = {}

def add_benchmark(name)
  BENCHMARKS[name] = yield
rescue Exception => e
  SKIPPED[name] = "#{e.class}: #{e.message}"
end

add_benchmark('Truffle::POSIX') { -> value { LabsPOSIX.labs(value) } }
add_benchmark('Full FFI') { -> value { LabsFFI.labs(value) } }

add_benchmark('Truffle NFI default') do
  nfi_default_labs = Truffle::POSIX.nfi_default_labs_function
  -> value { Truffle::Interop.execute(nfi_default_labs, value) }
end

add_benchmark('Truffle NFI panama') do
  nfi_panama_labs = Truffle::POSIX.nfi_panama_labs_function
  -> value { Truffle::Interop.execute(nfi_panama_labs, value) }
end

add_benchmark('Panama directly') { -> value { Truffle::POSIX.panama_labs(value) } }

BENCHMARKS.each do |name, call|
  result = call.call(ARGUMENT)
  raise "#{name} returned #{result.inspect}, expected #{EXPECTED}" unless result == EXPECTED
end

puts "RUBY_DESCRIPTION: #{RUBY_DESCRIPTION}"
SKIPPED.each do |name, reason|
  warn "Skipping #{name}: #{reason}"
end
puts

Benchmark.ips do |x|
  x.config(time: Integer(ENV.fetch('BENCHMARK_IPS_TIME', 5)),
           warmup: Integer(ENV.fetch('BENCHMARK_IPS_WARMUP', 2)))

  BENCHMARKS.each do |name, call|
    x.report(name) { call.call(ARGUMENT) }
  end

  x.compare!
end
