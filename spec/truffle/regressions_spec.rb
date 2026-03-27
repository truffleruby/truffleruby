# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Regression test suite" do
  it "fixes GitHub issue 2204" do
    /\A([0-9]+)_([_a-z0-9]*)\.?([_a-z0-9]*)?\.rb\z/.match("20190116152522_enable_postgis_extension.rb").to_a.should ==
      ["20190116152522_enable_postgis_extension.rb", "20190116152522", "enable_postgis_extension", ""]

    /\A([0-9]+)_([_a-z0-9]*)\.?([_a-z0-9]*)?\.rb\z/.match("20190116152523_create_schools.rb").to_a.should ==
      ["20190116152523_create_schools.rb", "20190116152523", "create_schools", ""]

    /^0{2}?(00)?(44)?(0)?([1-357-9]\d{9}|[18]\d{8}|8\d{6})$/.match("07123456789").to_a.should ==
      ["07123456789", nil, nil, "0", "7123456789"]

    /^0{2}?(00)?44/.match("447123456789").to_a.should ==
      ["44", nil]

    /^0{2}?(00)?(44)(0)?([1-357-9]\d{9}|[18]\d{8}|8\d{6})$/.match("447123456789").to_a.should ==
      ["447123456789", nil, "44", nil, "7123456789"]

    /^0{2}?(00)?(44)?(0)?([1-357-9]\d{9}|[18]\d{8}|8\d{6})$/.match("07123456789").to_a.should ==
      ["07123456789", nil, nil, "0", "7123456789"]

    /^0{2}?(00)?44/.match("447123456789").to_a.should ==
      ["44", nil]

    /^0{2}?(00)?(44)(0)?([1-357-9]\d{9}|[18]\d{8}|8\d{6})$/.match("447123456789").to_a.should ==
      ["447123456789", nil, "44", nil, "7123456789"]
  end
end
