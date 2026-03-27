# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

if RUBY_ENGINE == 'truffleruby'
  map = TruffleRuby::ConcurrentMap.new
  10_000.times { |i| map[i] = i }
  map[:a] = 1

  benchmark 'TruffleRuby::ConcurrentMap#[]' do
    map[:a]
  end

  benchmark 'TruffleRuby::ConcurrentMap#[]=' do
    map[:set] = :value
  end

  benchmark 'TruffleRuby::ConcurrentMap#each_pair' do
    map.each_pair { |k,v| k }
  end

  benchmark 'TruffleRuby::ConcurrentMap#delete' do
    map[:to_delete] = true
    map.delete(:to_delete)
  end
end
