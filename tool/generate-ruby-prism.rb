#!/usr/bin/env ruby
# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Generates lib/mri/ruby/prism.rb and lib/mri/ruby/prism/ from the Ruby files of the prism default gem
# (lib/mri/prism.rb and lib/mri/prism/), by renaming the Prism namespace to Ruby::Prism.
#
# Ruby::Prism is the Prism built into the interpreter, i.e. the version of Prism which parsed the running code,
# no matter which version of the prism gem is activated. See https://bugs.ruby-lang.org/issues/22379.
# This is the same rewrite as done by tool/sync_default_gems.rb in ruby/ruby.

require 'fileutils'

NAMESPACE = 'Ruby::Prism'
FEATURE = 'ruby/prism'

def rewrite(source)
  # Feature names, e.g. `require "prism/ffi"` and `autoload :Visitor, "prism/visitor"`
  source = source.gsub(/\b(require|autoload\s+:\w+,)(\s*)(["'])prism(?=["'\/])/) do
    "#{$1}#{$2}#{$3}#{FEATURE}"
  end
  # The namespace, also in comments and documentation
  source.gsub(/(?<![\w:])(::)?Prism\b/) { "#{$1}#{NAMESPACE}" }
end

lib = File.expand_path('../lib/mri', __dir__)

FileUtils.rm_rf ["#{lib}/#{FEATURE}.rb", "#{lib}/#{FEATURE}"]

Dir.glob(['prism.rb', 'prism/**/*'], base: lib).sort.each do |file|
  from = "#{lib}/#{file}"
  next if File.directory?(from)

  to = "#{lib}/ruby/#{file}"
  FileUtils.mkdir_p File.dirname(to)
  if file.end_with?('.rb')
    File.binwrite(to, rewrite(File.binread(from)))
  else
    FileUtils.cp from, to
  end
end
