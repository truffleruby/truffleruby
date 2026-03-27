# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "RUBY_ENGINE == 'truffleruby'", true
example "'truffleruby' == RUBY_ENGINE", true

example "defined?(::TruffleRuby) == 'constant'", true
