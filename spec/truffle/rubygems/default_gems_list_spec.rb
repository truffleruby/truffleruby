# Copyright (c) 2020, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../ruby/spec_helper'
require 'rubygems'

describe "Truffle::GemUtil::DEFAULT_GEMS" do
  it "is a Hash listing all default gem paths" do
    hash = Hash.new { |h,k| h[k] = [] }
    Dir.children("#{Gem.default_dir}/specifications/default").sort.each do |spec|
      name = spec[/^([\w-]+)-\d+(?:\.\d+)*\.gemspec$/, 1]
      raise spec unless name
      prefix = name.split("-").first # 'io' for gem 'io-console' required as 'io/console'
      hash[prefix] << name
    end
    hash = hash.to_h { |prefix, names| [prefix, names == [prefix] ? true : names] }

    # To copy-paste to gem_util.rb conveniently:
    #puts "\n\n"
    #puts '*' * 80
    #hash.each_pair { |k,v| puts "#{k.inspect} => #{v},".tr('"', "'") }
    #puts '*' * 80

    Truffle::GemUtil::DEFAULT_GEMS.should == hash
  end
end
