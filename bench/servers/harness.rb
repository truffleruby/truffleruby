#!/usr/bin/env ruby

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

query = /Requests per second:\s+(\d+\.\d+)\s+\[#\/sec\] \(mean\)/

budget = Integer(ARGV[0] || 240)
url = ARGV[1] || 'http://0.0.0.0:14873/'

tenth_budget = budget / 10
start_time = Time.now

requests_per_second = []

loop do
  elapsed = Time.now - start_time
  left_in_budget = budget - elapsed
  break if left_in_budget <= tenth_budget
  
  report = `ab -t #{tenth_budget} -n 5000 #{url}`
  abort unless $?.success?
  puts query.match(report)[1]
  
  # Always sleep, as we want to allow the ports we've just trashed to recover
  # before whatever is coming next is run.
  
  sleep 30
end
