# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module LastMatchFixtures

  MD = /(Rob)/.match "Robert"

  def self.foo()
    set_last_match(MD)
  end

  def self.set_last_match(data)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, data)
    data
  end

end

example "LastMatchFixtures.foo", LastMatchFixtures::MD
example "LastMatchFixtures.send(:foo)", LastMatchFixtures::MD
