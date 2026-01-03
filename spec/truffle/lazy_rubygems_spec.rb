# Copyright (c) 2018, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'

describe "RubyGems" do
  it "is not loaded until needed" do
    ruby_exe('puts $"').should_not include('/rubygems.rb')
    ruby_exe('p autoload? :Gem').should == "\"rubygems\"\n"
  end

  # This spec needs no upgraded gems installed
  it "is not loaded for default gems if there is no upgraded default gem" do
    default_gems = []
    keep_hyphen = %w[open-uri resolv-replace]

    Truffle::GemUtil::DEFAULT_GEMS.each_pair do |prefix, names|
      if names == true
        default_gems << prefix
      else
        default_gems.concat(names.map do |name|
          keep_hyphen.include?(name) ? name : name.tr('-', '/')
        end)
      end
    end
    default_gems -= [
      'bundler', # explicitly requires RubyGems
      'dbm', 'gdbm', # not available
      'debug', # not available
    ]
    default_gems[default_gems.index('english')] = 'English'

    code = <<-RUBY
    #{default_gems.inspect}.each do |name|
      require name
      unless autoload?(:Gem) == "rubygems"
        puts $LOADED_FEATURES
        abort "\#{name} loaded RubyGems"
      end
    end
    puts 'OK'
    RUBY
    ruby_exe(code, args: "2>&1").should == "OK\n"
    $?.success?.should == true
  end

  it "is loaded when accessing Gem" do
    ruby_exe('Gem; puts $"').should include('/rubygems.rb')
    ruby_exe('Gem; p autoload? :Gem').should == "nil\n"
  end

  it "is loaded by a failing require" do
    code = 'begin; require "lrg-does-not-exist"; rescue LoadError; puts $"; end'
    ruby_exe(code).should include('/rubygems.rb')
    $?.success?.should == true
  end
end

describe "Lazy RubyGems" do
  it "works for require 'rubygems/package'" do
    ruby_exe("require 'rubygems/package'; p Gem::Package").should == "Gem::Package\n"
  end

  it "works for require 'rubygems/specification'" do
    ruby_exe("require 'rubygems/specification'; p Gem::Specification").should == "Gem::Specification\n"
  end

  it "after loading RubyGems both Kernel#require and Kernel.require do not use the original require with lazy-rubygems loading" do
    code = <<~RUBY
      p Kernel.instance_method(:require) == Truffle::KernelOperations::ORIGINAL_REQUIRE
      p Kernel.method(:require) == Truffle::KernelOperations::ORIGINAL_MODULE_REQUIRE
      p Gem
      p Kernel.instance_method(:require) == Truffle::KernelOperations::ORIGINAL_REQUIRE
      p Kernel.instance_method(:require).source_location[0].end_with?("/rubygems/core_ext/kernel_require.rb>")
      p Kernel.method(:require) == Truffle::KernelOperations::ORIGINAL_MODULE_REQUIRE
    RUBY
    ruby_exe(code).should == <<~OUT
      true
      true
      Gem
      false
      true
      true
    OUT
  end

  it "Kernel.require should not be able to load gems" do
    code = 'begin; Kernel.require "prime"; rescue LoadError; p :load_error; end; require "prime"; p Prime'
    ruby_exe(code).should == ":load_error\nPrime\n"
  end
end
