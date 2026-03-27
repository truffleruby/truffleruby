# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

class MyException < StandardError
end

def recurse(n)
  if n == 0
    raise MyException, 'message'
  else
    recurse(n - 1)
  end
end

exc = nil

benchmark 'exception-stored-small-stack' do
  begin
    raise MyException, 'message'
  rescue MyException => e
    exc = e # captures the exception and the backtrace
  end
end

benchmark 'exception-stored-100-stack' do
  begin
    recurse(100)
  rescue MyException => e
    exc = e # captures the exception and the backtrace
  end
end
