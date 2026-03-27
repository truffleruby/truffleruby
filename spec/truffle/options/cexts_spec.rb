# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/spec_helper'

describe "The --cexts option" do
  it "when disabled can run basic expressions" do
    ruby_exe("p [1, 2, 3].map(&:succ)", options: "--experimental-options --cexts=false").should == "[2, 3, 4]\n"
  end

  it "when disabled can use reasonable parts of the stdlib" do
    ruby_exe("require 'uri'; p URI('http://foo.com/posts?id=30&limit=5#time=1305298413').query", options: "--experimental-options --cexts=false").should == "\"id=30&limit=5\"\n"
  end

  it "when disabled cannot use parts of the stdlib that use C extensions" do
    ruby_exe("require 'yaml'; p YAML.load('--- foo')", options: "--experimental-options --cexts=false", args: "2>&1", exit_status: 1).should.include?("cannot load as C extensions are disabled")
  end
end
