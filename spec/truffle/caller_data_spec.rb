# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

module TruffleCallerSpecFixtures
  def self.last_line_set(last_line)
    Primitive.io_last_line_set(Primitive.caller_special_variables, last_line)
    last_line
  end

  def self.last_match_set(match)
    Primitive.regexp_last_match_set(Primitive.caller_special_variables, match)
    match
  end
end

describe "A caller" do

  it "can have its special variables read and modified" do
    last_line = "Hello!"
    md = Primitive.matchdata_create_single_group(/o/, "Hello", 4, 5)
    TruffleCallerSpecFixtures.last_line_set(last_line)
    TruffleCallerSpecFixtures.last_match_set(md)
    $_.should == last_line
    $~.should == md
  end

  it "can have its special variables read and modified through an intermediate #send" do
    last_line = "Hello!"
    md = Primitive.matchdata_create_single_group(/o/, "Hello", 4, 5)
    TruffleCallerSpecFixtures.send(:last_line_set, last_line)
    TruffleCallerSpecFixtures.send(:last_match_set, md)
    $_.should == last_line
    $~.should == md
  end
end
