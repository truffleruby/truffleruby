# Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "The parse-time name of methods" do
  it "gives an accurate description" do
    ruby_exe(fixture(__FILE__, "parse_name.rb"), args: "2>&1").should == <<-OUT
M::C#regular_instance_method
M::C.sdef_class_method
M::C.sclass_method
Object#top_method
main.sdef_method_of_main
main.sclass_method_of_main
unknown_def_singleton_method
unknown_sdef_singleton_method
String.string_class_method
Integer#+
Kernel#then
OUT
  end
end
