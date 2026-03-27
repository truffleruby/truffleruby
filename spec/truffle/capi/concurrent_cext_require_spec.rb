# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

describe "Requiring C extensions concurrently" do
  it "is thread-safe" do
    out = ruby_exe(fixture(__FILE__ , 'concurrent_cext_require.rb'), args: "2>&1")
    out.should == "success\n"
    $?.success?.should == true
  end
end
