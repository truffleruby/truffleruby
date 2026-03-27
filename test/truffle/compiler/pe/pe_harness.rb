# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

abort 'not running the GraalVM Compiler' unless TruffleRuby.jit?

def create_test_pe_code_method(code)
  Object.class_eval <<-RUBY, __FILE__, __LINE__+1
    # truffleruby_primitives: true
    def test_pe_code
      value = Primitive.assert_compilation_constant(begin; #{code}; end)
      Primitive.assert_not_compiled
      value
    end
  RUBY
end
