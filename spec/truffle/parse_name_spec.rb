# Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

# Similar to spec/ruby/core/thread/backtrace/location/label_spec.rb
# but testing the parse-time name
describe "The parse-time name of methods" do
  it "gives an accurate description" do
    ruby_exe(fixture(__FILE__, "parse_name.rb"), args: "2>&1").should == <<-OUT
M::C#regular_instance_method
M::C.sdef_class_method
M::C.sclass_method
block (2 levels) in M::C.block_in_sclass_method
M::D#scoped_method
M::D.sdef_scoped_method
M::D.sclass_scoped_method
Top#top
Top::Nested#top_nested
Top::Nested::C#top_nested_c
Object#top_method
main.sdef_method_of_main
main.sclass_method_of_main
unknown_def_singleton_method
unknown_sdef_singleton_method
module_eval_method
sdef_module_eval_method
String.string_class_method
nested_class_method
Enumerable#mod_function
Enumerable#mod_function
Integer#+
Kernel#then
OUT
  end
end

# Tested here to help comparison with the above
describe "The RootNode#getName() of methods" do
  it "gives an accurate description" do
    ruby_exe(fixture(__FILE__, "parse_name.rb"), args: "root_node_name 2>&1").should == <<-OUT
M::C#regular_instance_method
M::C.sdef_class_method
M::C.sclass_method
block (2 levels) in M::C.block_in_sclass_method
M::D#scoped_method
M::D.sdef_scoped_method
M::D.sclass_scoped_method
Top#top
Top::Nested#top_nested
Top::Nested::C#top_nested_c
Object#top_method
sdef_method_of_main
sclass_method_of_main
unknown_def_singleton_method
unknown_sdef_singleton_method
Object#module_eval_method
Object.sdef_module_eval_method
String.string_class_method
String.nested_class_method
Enumerable#mod_function
Enumerable#mod_function
Integer#+
Kernel#then
OUT
  end
end
