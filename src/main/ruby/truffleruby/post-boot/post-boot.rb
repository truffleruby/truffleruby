# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# These files are loaded during context pre-initialization to save startup time
# Always provided features: ruby --disable-gems -e 'puts $"'
begin
  require 'enumerator'
  require 'thread'
  require 'fiber'
  require 'rational'
  require 'complex'
rescue LoadError => e
  Truffle::Debug.log_warning "#{File.basename(__FILE__)}:#{__LINE__} #{e.message}"
end

if Truffle::Boot.get_option_or_default('did-you-mean', true)
  # Load DidYouMean here manually, to avoid loading RubyGems eagerly
  Truffle::Boot.print_time_metric :'before-did-you-mean'
  begin
    gem_original_require 'did_you_mean'
  rescue LoadError => e
    Truffle::Debug.log_warning "#{File.basename(__FILE__)}:#{__LINE__} #{e.message}"
  ensure
    Truffle::Boot.print_time_metric :'after-did-you-mean'
  end
end

# Post-boot patching when using context pre-initialization
if Truffle::Boot.preinitializing?
  old_home = Truffle::Boot.ruby_home
  # We need to fix all paths which capture the image build-time home to point
  # to the runtime home.

  paths_starting_with_home = []
  [$LOAD_PATH, $LOADED_FEATURES].each do |array|
    array.each do |path|
      if path.start_with?(old_home)
        path.replace Truffle::Debug.flatten_string(path[old_home.size..-1])
        paths_starting_with_home << path
      elsif !path.include?('/')
        # relative path for always provided features like 'ruby2_keywords.rb'
      else
        raise "Path #{path.inspect} in $LOAD_PATH or $LOADED_FEATURES was expected to start with #{old_home}"
      end
    end
  end
  old_home = nil # Avoid capturing the old home in the blocks below

  Truffle::FeatureLoader.clear_cache

  Truffle::Boot.delay do
    new_home = Truffle::Boot.ruby_home
    paths_starting_with_home.each do |path|
      path.replace(new_home + path)
    end
  end
end

Truffle::Boot.delay do
  wd = Truffle::Boot.get_option('working-directory')
  Dir.chdir(wd) unless wd.empty? || wd == '.'
end

Truffle::Boot.delay do
  if Truffle::Boot.get_option('rubygems') and !Truffle::Boot.get_option('lazy-rubygems')
    begin
      Truffle::Boot.print_time_metric :'before-rubygems'
      begin
        # Needs to happen after patching $LOAD_PATH above
        require 'rubygems'
      ensure
        Truffle::Boot.print_time_metric :'after-rubygems'
      end
    rescue LoadError => e
      Truffle::Debug.log_warning "#{File.basename(__FILE__)}:#{__LINE__} #{e.message}\n#{$LOAD_PATH.join "\n"}"
    end
  end
end
