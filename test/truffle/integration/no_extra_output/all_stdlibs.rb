# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2017-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require 'rbconfig'

def glob(pattern)
  files = Dir.glob(pattern)
  raise "no libraries found with #{pattern}" if files.empty?
  files
end

stdlibs = []

glob("#{RbConfig::CONFIG['rubylibdir']}/*.rb").each { |file|
  stdlibs << File.basename(file, '.*')
}

glob("#{RbConfig::CONFIG['archdir']}/*.#{RbConfig::CONFIG['DLEXT']}").each { |file|
  stdlibs << File.basename(file, '.*')
}

glob("#{RbConfig::CONFIG['prefix']}/lib/truffle/*.rb").each { |file|
  stdlibs << File.basename(file, '.*')
}

glob("#{RbConfig::CONFIG['rubylibdir']}/net/*.rb").each { |file|
  stdlibs << "net/#{File.basename(file, '.*')}"
}

stdlibs += %w[json]

# 'continuation' warns on being required, as MRI
# Others fail to load
ignore = %w[
  continuation
]

stdlibs -= ignore

stdlibs.uniq!

stdlibs.each { |lib| require lib }

puts 3 * 4
