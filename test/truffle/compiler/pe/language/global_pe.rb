# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

$stable_global = 42

example "$stable_global", 42

$almost_stable_global = 1
$almost_stable_global = 2

example "$almost_stable_global", 2

100.times { |i|
  $same_value_global = 21
}

example "$same_value_global", 21

100.times { |i|
  $unstable_global = i
}

counter example "$unstable_global"
