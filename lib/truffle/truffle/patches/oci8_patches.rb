# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class OCI8Patches

  RBOCI8TYPE_DECL_ORIGINAL = <<-EOF
    switch (type) {
    case T_DATA:
        klass = CLASS_OF(obj);
        if (klass == cOCINumber) {
  EOF

  RBOCI8TYPE_DECL_NEW = <<-EOF
    switch (type) {
    case T_DATA:
    case T_OBJECT:
        klass = CLASS_OF(obj);
        if (klass == cOCINumber) {
  EOF

  PATCHES = {
      gem: 'ruby-oci8',
      patches: {
          ['oci8', 'ocinumber.c'] => [
              {   # The current bigdecimal implementation has type T_OBJECT
                  # and OCI8 expects the usual T_DATA type. This patch can be
                  # removed once the bigdecimal C extension is always used.
                  match: RBOCI8TYPE_DECL_ORIGINAL,
                  replacement: RBOCI8TYPE_DECL_NEW
              },
          ],
      }
  }

end
