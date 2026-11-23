# frozen_string_literal: true
# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# racc's C extension runtime (cparse) calls back into Ruby for almost every parser
# table lookup (rb_ary_entry, rb_hash_aref, RARRAY_LEN, etc.). On TruffleRuby, each of
# those is an upcall, which makes cparse much slower than racc's pure-Ruby runtime. The
# racc gem bundled with TruffleRuby is already installed without cparse. But, a racc
# gem installed by Bundler or RubyGems (e.g., as a dependency of nokogiri) builds it.
# racc falls back to its pure-Ruby runtime whenever requiring cparse raises a LoadError,
# so we raise one. That also covers parsers generated with an embedded runtime
# (racc -E), since they contain the same require.
#
# Defining a top-level Racc_No_Extensions = false (or any falsy value, matching racc's
# own check) before loading racc opts back in to cparse. Only the top-level constant
# counts. Unless the top-level constant already exists, racc defines
# Racc::Racc_No_Extensions = false itself before requiring cparse, so we can't tell an
# explicit opt-in on the nested constant apart from racc's default.
if defined?(::Racc_No_Extensions) && !::Racc_No_Extensions
  require Primitive.get_original_require(__FILE__)
else
  # Set the path the same way a missing file would, since some code checks it to see which file
  # failed to load.
  error = LoadError.new('TruffleRuby uses the pure-Ruby racc runtime as it is faster than racc/cparse')
  error.path = 'racc/cparse'
  raise error
end
