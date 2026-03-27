# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/optional/capi/spec_helper'

load_extension("error")

# C functions defined in CRuby internal headers, so they don't belong to the public API.
describe "C-API error functions" do
  before :each do
    @e = CApiErrorSpecs.new

    # save current flags
    @deprecated = Warning[:deprecated]
    @experimental = Warning[:experimental]
  end

  after :each do
    # restore flags
    Warning[:deprecated] = @deprecated
    Warning[:experimental] = @experimental
  end

  describe "rb_warning_category_enabled_p" do
    it "returns Warning[:deprecated] when argument is RB_WARN_CATEGORY_DEPRECATED" do
      Warning[:deprecated] = true
      @e.rb_warning_category_enabled_p_deprecated.should == true

      Warning[:deprecated] = false
      @e.rb_warning_category_enabled_p_deprecated.should == false
    end

    it "returns Warning[:experimental] when argument is RB_WARN_CATEGORY_EXPERIMENTAL" do
      Warning[:experimental] = true
      @e.rb_warning_category_enabled_p_experimental.should == true

      Warning[:experimental] = false
      @e.rb_warning_category_enabled_p_experimental.should == false
    end

    it "return true for any other numeric value" do
      @e.rb_warning_category_enabled_p(10).should == true
    end
  end
end
