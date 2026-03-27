# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class Method
  def inspect
    Truffle::MethodOperations.inspect_method(self, Primitive.metaclass(receiver), owner, receiver)
  end
  alias_method :to_s, :inspect

  def curry(curried_arity = nil)
    self.to_proc.curry(curried_arity)
  end

  def >>(other)
    self.to_proc >> other
  end

  def <<(other)
    self.to_proc << other
  end
end
