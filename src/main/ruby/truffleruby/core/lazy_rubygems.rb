# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Delay this so the pre-initialized context can also be used with --disable-gems
# Otherwise, --disable-gems would degrade startup which is counter-intuitive.
Truffle::Boot.delay do
  if Truffle::Boot.get_option 'lazy-rubygems'
    module Kernel
      private def gem(*args)
        gem_original_require 'rubygems'
        gem(*args)
      end
    end

    class Object
      autoload :Gem, 'rubygems'

      # RbConfig is required by RubyGems, which makes it available in Ruby by default.
      # Autoload it since we do not load RubyGems eagerly.
      autoload :RbConfig, 'rbconfig'
      # Defined by RbConfig
      autoload :CROSS_COMPILING, 'rbconfig'
    end
  end
end
