# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Defaults for RubyGems when running on TruffleRuby.
# This file is required by RubyGems when RubyGems is loaded.

module Gem
  # The path to the gems shipped with TruffleRuby
  def self.default_dir
    @default_dir ||= "#{Truffle::Boot.ruby_home}/lib/gems"
  end

  # Only report the RUBY platform as supported to make sure gems precompiled for MRI are not used.
  # TruffleRuby has a different ABI and cannot reuse gems precompiled for MRI.
  # See https://github.com/rubygems/rubygems/issues/2945
  Gem.platforms = [Gem::Platform::RUBY]

  def self.platform_defaults
    # disable documentation by default as it takes a significant amount of time for installing gems and is rarely used
    { 'gem' => '--no-document' }
  end
end

class Gem::Platform
  # The list of gems we want to install precompiled (using the local platform) on TruffleRuby
  REUSE_AS_BINARY_ON_TRUFFLERUBY = %w[libv8 libv8-node sass-embedded sorbet-static tailwindcss-ruby] + Truffle::Boot.get_option('reuse-precompiled-gems')
end
