# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module IVarFixtures
  class Foo
    attr_reader :a

    def initialize(a, b)
      @a = a
      @b = b
    end

    def b
      @b
    end

    def reset_b(b)
      @b = b
      self
    end

    def ivar_get_a
      instance_variable_get :@a
    end
  end
end

example "IVarFixtures::Foo.new(1,2).a", 1
example "IVarFixtures::Foo.new(1,2).b", 2

example "IVarFixtures::Foo.new(1,2).reset_b(42).b", 42
example "IVarFixtures::Foo.new(1,2).reset_b([]).b.empty?", true

example "IVarFixtures::Foo.new(1,2).ivar_get_a", 1
