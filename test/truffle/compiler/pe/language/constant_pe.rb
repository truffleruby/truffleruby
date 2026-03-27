# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module ConstantFixtures
  A = 1

  def self.get_existing
    Proc
  end

  def self.get
    A
  end

  def self.const_get_in_scope(name)
    ConstantFixtures.const_get(name)
  end

  def self.const_get_inherit_false(name)
    ConstantFixtures.const_get(name, false)
  end

  module Nested
    def self.get_nested
      A
    end
  end

  class Base
    B = 2
  end

  class Child < Base
    def self.get_inherited
      B
    end
  end

  module ConstMissing
    def self.get_missing
      NO_SUCH_NAMED_CONSTANT
    end

    def self.const_missing(const)
      const
    end
  end
end

example "ConstantFixtures.get_existing", Proc
example "ConstantFixtures.get", 1

example "ConstantFixtures.const_get_in_scope(:A)", 1
example "ConstantFixtures.const_get_in_scope('A')", 1

counter example "ConstantFixtures.const_get_inherit_false(:A)"
counter example "ConstantFixtures.const_get_inherit_false('A')"

example "ConstantFixtures::Nested.get_nested", 1
example "ConstantFixtures::Child.get_inherited", 2

example "ConstantFixtures::ConstMissing.get_missing", :NO_SUCH_NAMED_CONSTANT
