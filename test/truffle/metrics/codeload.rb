# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

5_000.times do |n|
  eval %{
    def foo#{n}(a, b, c)
      if a == b
        a + b + c # comments
      else
        a.foo + b.foo + c.foo
      end
    end
    
    class Foo#{n}
      def bar#{n}
        [#{n}, #{n} + 1, #{n} + 2]
      end
      
      def baz#{n}
        {a: #{n}, b: #{n + 1}, c: #{n + 2}}
      end
    end
  }
end
