# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
# 
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

example "Truffle::ThreadOperations.detect_recursion(Object.new) { }", false
example "Truffle::ThreadOperations.detect_recursion([]) { }", false
example "Truffle::ThreadOperations.detect_recursion({}) { }", false

example "y = nil; Truffle::ThreadOperations.detect_recursion(Object.new) { y = Truffle::ThreadOperations.detect_recursion(Object.new) { } }; y", false
example "x = Object.new; y = nil; Truffle::ThreadOperations.detect_recursion(x) { y = Truffle::ThreadOperations.detect_recursion(x) { } }; y", true

def detect_recursion_recursive(method, object)
  Truffle::ThreadOperations.detect_recursion(object) do
    object.send(method) do |child|
      return detect_recursion_recursive(method, child)
    end
  end
end

example "detect_recursion_recursive(:each, [])", false
example "detect_recursion_recursive(:each_value, {})", false
