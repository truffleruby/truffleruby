# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2020-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../ruby/spec_helper'
require 'rubygems'

describe "Default gem specs" do
  it "@path_to_default_spec_map should be non-empty" do
    Gem.instance_variable_get(:@path_to_default_spec_map).empty?.should == false
  end

  it "do not contain platform-specific native extensions" do
    dlext = ".#{RbConfig::CONFIG['DLEXT']}"
    Dir.glob("#{Gem.default_dir}/specifications/default/*.gemspec").sort.each do |spec|
      contents = File.read(spec)
      contents.should_not include(dlext)
    end
  end
end
