# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Binding
  def eval(code, file = nil, line = nil)
    Kernel.eval(code, self, file, line)
  end

  def local_variables
    Primitive.local_variable_names(self).dup
  end
  Primitive.always_split self, :local_variables

  def irb
    require 'irb'
    irb
  end
end
