# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Tested with tk version 0.4.0
class TkPatches

  PATCHES = {
    gem: 'tk',
    non_standard_dir_structure: true,
    patches: {
      ['tk/tkutil', 'tkutil.c'] => [
        {
          match: 'st_foreach_check(RHASH_TBL(keys), to_strkey, new_keys, Qundef)',
          replacement: 'rb_hash_foreach(keys, to_strkey, new_keys)'
        },
        {
          match: 'st_foreach_check(RHASH_TBL(hash), push_kv, args, Qundef)',
          replacement: 'rb_hash_foreach(hash, push_kv, args)'
        },
        {
          match: 'st_foreach_check(RHASH_TBL(hash), push_kv_enc, args, Qundef)',
          replacement: 'rb_hash_foreach(hash, push_kv_enc, args)'
        },
      ]
    }
  }

end
