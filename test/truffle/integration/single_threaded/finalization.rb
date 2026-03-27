# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

x = Object.new

ObjectSpace.define_finalizer x, proc { p :x; exit! 0 }

x = nil

GC.start

sleep 1
p :after_sleep

y = Object.new

# Defining a new finalizer should cause the old finalizer to run in this thread

ObjectSpace.define_finalizer y, proc { p :y; exit! 1 }

# should not be reached
exit! 2
