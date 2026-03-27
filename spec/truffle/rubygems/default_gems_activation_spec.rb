# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../../ruby/spec_helper'
require 'rubygems'

describe "Default gems activation" do
  GEM_TO_ENTRY_POINT_EXCEPTIONS = {
    # 'open-uri' and 'resolv-replace' gems are exceptions of the general rule for compound gem names
    'open-uri' => 'open-uri',
    'resolv-replace' => 'resolv-replace',

    # rinda gem cannot be required by single 'require "rinda"', only by requiring some of its files, e.g. 'rinda/tuplespace'
    'rinda' => 'rinda/tuplespace',

    # by some reason the 'english' gem is implemented in English.rb file so should required by 'require "English"'
    'english' => 'English'
  }

  # Returns default gem names and paths to require these gems
  def gems_entry_points
    # Truffle::GemUtil::DEFAULT_GEMS.keys could be used instead (a far as its correctness is tested)
    filenames = Dir.children(Gem.default_specifications_dir)

    filenames.sort.map do |filename|
      # extract gem name from a gem specification file
      # e.g. 'io-wait' from 'io-wait-0.3.0.gemspec' and 'psych' from 'psych-5.0.1.gemspec'
      gem_name = filename[/^([\w-]+)-\d+(?:\.\d+)*\.gemspec$/, 1]

      # transform gem names into paths to require with Kernel#require.
      if GEM_TO_ENTRY_POINT_EXCEPTIONS.key?(gem_name)
        entry_point = GEM_TO_ENTRY_POINT_EXCEPTIONS[gem_name]
      else
        # for simple gem names (e.g. 'zlib') a path to require is a gem name, e.g. 'require "zlib"';
        # for compound gem names (with a suffix separated with '-', e.g. 'net-protocol') a path to require contains
        # a suffix as a path section (e.g. 'net/protocol').
        entry_point = gem_name.gsub("-", "/")
      end

      [gem_name, entry_point]
    end
  end

  gems_entry_points.each do |gem_name, entry_point|
    it "should activate a default gem #{gem_name} by requiring #{entry_point}" do
      code = <<~RUBY
        if Gem.loaded_specs.keys.include?("#{gem_name}")
          puts "skip"
        else
          require "#{entry_point}"
          puts Gem.loaded_specs.keys.include?("#{gem_name}")
        end
      RUBY

      ruby_exe(code, args: "2>&1").should =~ /\Atrue|skip\Z/
    end
  end
end
