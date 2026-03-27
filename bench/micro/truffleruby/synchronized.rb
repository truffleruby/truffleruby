# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

if RUBY_ENGINE == 'truffleruby'
  existing = Object.new
  benchmark 'core-truffleruby-synchronized-same-object' do
    TruffleRuby.synchronized(existing) {}
  end

  benchmark 'core-truffleruby-synchronized-new-object' do
    obj = Object.new
    TruffleRuby.synchronized(obj) {}
  end
end
