# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../lib/truffle/truffle/cext_preprocessor'

def test_patch(file, directory, input, expected)
  got = Truffle::CExt::Preprocessor.patch(file, input, directory)
  abort "expected\n#{expected}\ngot\n#{got}" unless got == expected
end

json_original = <<-EOF
# else
rb_str_resize(*result, RSTRING_LEN(*result));
# endif
EOF

json_patched = <<-EOF
# else

# endif
EOF

# Tests an empty replacement
test_patch 'parser.c', 'ext/json/ext/parser', json_original, json_patched

# Should not patch other files or other gems
test_patch 'other_file.c', 'ext/json/ext/parser', json_original, json_original
test_patch 'parser.c', 'ext/other_gem', json_original, json_original