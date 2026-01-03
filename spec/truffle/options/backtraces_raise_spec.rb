# Copyright (c) 2019, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --backtraces-raise option" do
  it "prints a backtrace on #raise" do
    file = fixture __FILE__ , 'raise_rescue.rb'
    out = ruby_exe(file, options: "--experimental-options --backtraces-raise", args: "2>&1")
    out.should ==  <<~OUTPUT
    raise: #{file}:10:in 'Object#some_method': error (RuntimeError)
    \tfrom #{file}:15:in '<main>'
    OUTPUT
  end
end
