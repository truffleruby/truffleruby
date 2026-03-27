# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require File.expand_path('clamp', File.dirname(__FILE__))

benchmark { clamp_a(10, 40, 90) }
benchmark('clamp_a') { clamp_a(10, 40, 90) }
benchmark('clamp_b') { clamp_b(10, 40, 90) }
