# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

ary = (1..1000).to_a

benchmark 'core-integer-modulo-power2' do
  ary.reduce { |sum, e| sum + e % 8 }
end
