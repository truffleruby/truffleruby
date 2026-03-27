# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class JsonPatches
  PATCHES = {
    gem: 'json',
    patches: {
      ['parser', 'parser.c'] => [
        { # JSON_parse_string
          # Work around a bug in the json extension where it tries to call `rb_str_resize` on non-String objects.
          # We remove it entirely because the string resize is an MRI-optimization to reduce large preallocated strings
          # to embedded strings. We don't have that distinction in our implementation and the resize would be a wasteful operation.
          match: /rb_str_resize\(\*result, RSTRING_LEN\(\*result\)\);/,
          replacement: ''
        },
      ]
    }
  }
end
