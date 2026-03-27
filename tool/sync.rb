#!/usr/bin/env ruby

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# A script to synchronize changes from the Ruby source files
# to the GraalVM build instantaneously.

begin
  require 'listen'
rescue LoadError
  abort "Gem 'listen' not installed. Install with 'gem install listen'."
end

require 'fileutils'

ruby_home = ARGV[0] or raise "Need the TruffleRuby home as first argument"

FROM = File.expand_path('../..', File.realpath(__FILE__))
TO = File.expand_path(ruby_home)

abort "#{TO} does not exist" unless File.directory?(TO)
abort "#{TO} is not a TruffleRuby home" unless File.directory?("#{TO}/lib/truffle")

# Must be consistent with TRUFFLERUBY_GRAALVM_SUPPORT_PLATFORM_AGNOSTIC in suite.py
DIRS_TO_SYNC = %w[
  lib/cext
  lib/json
  lib/mri
  lib/patches
  lib/truffle
  lib/gems
].map { |path| "#{FROM}/#{path}" }

listener = Listen.to(*DIRS_TO_SYNC) do |modified, added, removed|
  (modified + added).each do |file|
    target = "#{TO}#{file[FROM.size..-1]}"
    FileUtils::Verbose.cp file, target
  end
  removed.each do |file|
    target = "#{TO}#{file[FROM.size..-1]}"
    FileUtils::Verbose.rm target
  end
end

listener.start # not blocking
sleep
