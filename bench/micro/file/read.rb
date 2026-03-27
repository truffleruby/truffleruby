# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

zero = File.open('/dev/zero')

benchmark 'core-read-kilobyte' do
  zero.read 1024
end

benchmark 'core-read-gigabyte' do
  zero.read 1024 * 1024 * 1024
end
