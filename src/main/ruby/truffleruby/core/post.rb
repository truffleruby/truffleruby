# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2015-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# Copyright (c) 2007-2015, Evan Phoenix and contributors
# All rights reserved.
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# * Redistributions of source code must retain the above copyright notice, this
#   list of conditions and the following disclaimer.
# * Redistributions in binary form must reproduce the above copyright notice
#   this list of conditions and the following disclaimer in the documentation
#   and/or other materials provided with the distribution.
# * Neither the name of Rubinius nor the names of its contributors
#   may be used to endorse or promote products derived from this software
#   without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

# Set up IO only now as it has lots of dependencies
STDIN = IO.new(0, IO::RDONLY)
STDOUT = IO.new(1, IO::WRONLY)
STDERR = IO.new(2, IO::WRONLY)

$stdin = STDIN
$stdout = STDOUT
$stderr = STDERR

class << STDIN
  def external_encoding
    super || Encoding.default_external
  end
end

Truffle::Boot.delay do
  # stdout is line-buffered if it refers to a terminal
  if Truffle::Boot.get_option('polyglot-stdio') || !Truffle::POSIX::NATIVE || STDOUT.tty?
    STDOUT.sync = true
  end
end

# stderr is always unbuffered, see setvbuf(3)
STDERR.sync = true

module Truffle
  module Type
    def self.const_get(mod, name, inherit = true, resolve = true)
      raise 'unsupported' unless resolve
      mod.const_get name, inherit
    end

    def self.const_exists?(mod, name, inherit = true)
      mod.const_defined? name, inherit
    end
  end
end

# Only define Kernel#p here when everything is set up so the basic version is
# available while loading core where IO is not fully defined.
module Kernel
  def p(*a)
    return nil if a.empty?
    a.each { |obj| $stdout.puts obj.inspect }
    $stdout.flush

    a.size == 1 ? a.first : a
  end
  module_function :p
end

Truffle::Boot.delay do
  $$ = Truffle::POSIX::NATIVE ? Truffle::POSIX.getpid : nil

  ARGV.concat(Truffle::Boot.original_argv)
end

yield_self do # Avoid capturing ruby_home in the at_exit and delay blocks
  ruby_home = Truffle::Boot.ruby_home

  # Does not exist but it's used by rubygems to determine index where to insert gem lib directories, as a result
  # paths supplied by -I will stay before gem lib directories. See Gem.load_path_insert_index in rubygems.rb.
  # Must be kept in sync with the value of RbConfig::CONFIG['sitelibdir'].
  $LOAD_PATH.push "#{ruby_home}/lib/ruby/site_ruby/#{Truffle::GemUtil::ABI_VERSION}"

  $LOAD_PATH.push "#{ruby_home}/lib/truffle"
  $LOAD_PATH.push "#{ruby_home}/lib/mri"
  $LOAD_PATH.push "#{ruby_home}/lib/json/lib"

  $LOAD_PATH.each { |p| p.instance_variable_set(:@gem_prelude_index, p) }
end

Truffle::Boot.delay do
  extra_load_paths = Truffle::Boot.extra_load_paths
  unless extra_load_paths.empty?
    $LOAD_PATH.unshift(*extra_load_paths.map { |path| File.expand_path(path) })
  end
end

# Set class is in the core library but it's still possible to require it as a standard
# library with `require`. So it doesn't make sense to have two copies of the same code
# and it's easier just to autoload CRuby's provided source file here.
autoload :Set, 'set'
