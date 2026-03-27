# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2016-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# There must be no local variables at the top scope in this file,
# as the TOPLEVEL_BINDING should be empty until the main script is executed.
TOPLEVEL_BINDING = Primitive.create_empty_binding

# The Binding used for sharing top-level locals of interactive Sources
Truffle::Boot::INTERACTIVE_BINDING = Primitive.create_empty_binding

module Truffle::Boot

  def self.check_syntax(source_or_file)
    inner_check_syntax source_or_file
    STDOUT.puts 'Syntax OK'
  rescue SyntaxError => e
    STDERR.puts "SyntaxError in #{e.message}"
    raise e
  end
  private_class_method :check_syntax

  def self.find_s_file(name)
    # Nonstandard lookup

    ruby_home = Truffle::Boot.ruby_home
    # added to look up truffleruby own files first when it's not on PATH
    name_in_ruby_home_bin = "#{ruby_home}/bin/#{name}"
    return name_in_ruby_home_bin if File.exist?(name_in_ruby_home_bin)

    # Standard lookups

    if ENV['RUBYPATH']
      path = find_in_environment_paths(name, ENV['RUBYPATH']) and return path
    end
    path = find_in_environment_paths(name, ENV['PATH']) and return path
    return name if File.exist?(name)

    # Not found, let the RubyLauncher print the error
    nil
  end

  def self.find_in_environment_paths(name, env_value)
    env_value.to_s.split(File::PATH_SEPARATOR).each do |path|
      name_in_path = "#{path}/#{name}"
      return name_in_path if File.exist?(name_in_path)
    end
    nil
  end
  private_class_method :find_in_environment_paths

end
