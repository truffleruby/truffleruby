# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'

describe "The standalone" do
  sulong = "#{Truffle::Boot.ruby_home}/lib/sulong"

  guard -> { Dir.exist? sulong } do # check if standalone, see standalone_dependencies
    it "only includes the necessary Sulong libs" do
      Dir.glob("#{sulong}/*").filter_map { File.basename(_1) if Dir.exist?(_1) }.should == %w[include native]
      Dir.glob("#{sulong}/native/*").map { File.basename(_1) }.should == %w[lib]
      Dir.glob("#{sulong}/native/lib/*") do
        File.basename(_1).should_not.include?('++')
      end
    end

    it "does not include the llvm toolchain" do
      Dir.should_not.exist? "#{Truffle::Boot.ruby_home}/lib/llvm-toolchain"
    end
  end
end
