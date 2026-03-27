# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class UnboundMethod
  def inspect
    Truffle::MethodOperations.inspect_method(self, owner, owner)
  end
  alias_method :to_s, :inspect

  def bind_call(recv, ...)
    bind(recv).call(...)
  end
  Primitive.always_split self, :bind_call
end
