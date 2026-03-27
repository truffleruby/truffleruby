# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Tested with pg version 0.21.0
class PgPatches

  PG_TEXT_DECODER_FREE_OLD = <<-EOF
static VALUE
pg_text_dec_bytea(t_pg_coder *conv, const char *val, int len, int tuple, int field, int enc_idx)
EOF

  PG_TEXT_DECODER_FREE_NEW = <<-EOF
static VALUE pg_tr_pq_freemem(VALUE mem) {
  PQfreemem((void *)mem);
  return Qfalse;
}

static VALUE
pg_text_dec_bytea(t_pg_coder *conv, const char *val, int len, int tuple, int field, int enc_idx)
EOF

  PATCHES = {
    gem: 'pg',
    patches: {
      'pg_result.c' => [
        {
          match: 'xmalloc(',
          replacement: 'calloc(1,'
        },
      ],
      'pg_tuple.c' => [
        {
          match: 'xmalloc(',
          replacement: 'calloc(1,'
        },
      ],
      'pg_text_decoder.c' => [
        {
          match: PG_TEXT_DECODER_FREE_OLD,
          replacement: PG_TEXT_DECODER_FREE_NEW
        },
        {
          match: '(VALUE(*)())PQfreemem',
          replacement: 'pg_tr_pq_freemem'
        }
      ],
      'pg_text_encoder.c' => [
        {
          match: 'if(TYPE(*intermediate) == T_FIXNUM)',
          replacement: 'if(TYPE(*intermediate) == T_FIXNUM && NUM2LL(*intermediate) != -(NUM2LL(*intermediate)))'
        },
      ],
    }
  }
end
