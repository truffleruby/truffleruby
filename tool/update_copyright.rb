# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2014-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

abort "USAGE: [ADD_ONLY=1] ruby #{$0} NEW_YEAR" unless ARGV.first
year = Integer(ARGV.first)
new_copyright_year = year

RB_COPYRIGHT = <<-EOS
# Copyright (c) #{new_copyright_year}, TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

EOS

JAVA_COPYRIGHT = <<-EOS
/*
 * Copyright (c) #{new_copyright_year} TruffleRuby contributors
 * This code is released under a tri EPL/GPL/LGPL license.
 * You can use it, redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
EOS

C_COPYRIGHT = JAVA_COPYRIGHT + "\n"

NEW_COPYRIGHT = {
  '.rb' => RB_COPYRIGHT,
  '.java' => JAVA_COPYRIGHT,
  '.c' => C_COPYRIGHT,
  '.h' => C_COPYRIGHT,
}

EXTENSIONS = %w[.java .rb .c .h .md]

COPYRIGHT = /(?<copyright>Copyright|copyright) \(c\) (?<year1>\d{4})(?:-(?<year2>\d{4}))?(?<comma>,)? (?<holder>TruffleRuby contributors)\b/

OTHER_COPYRIGHTS = [
  /Copyright \(c\) \d{4}(?:-\d{4})?,? Evan Phoenix/,
  /Copyright \(c\) \d{4} Engine Yard/,
  /Copyright \(c\) \d{4} Akinori MUSHA/, # SHA-2
  /Copyright \(c\) \d{4}-\d{4} The JRuby project/, # io/console
  /Copyright \(C\) \d{4}-\d{4} Wayne Meissner/, # FFI
  /Copyright \(c\) \d{4}, Brian Shirai/, # rubysl-socket
  /Copyright \(c\) \d{4}, \d{4} Todd C\. Miller <Todd\.Miller@courtesan\.com>/, # strlcpy.c
  /Copyright \(C\) \d{4} Koichi Sasada/, # id.h
  /Ruby is copyrighted free software by Yukihiro Matsumoto/, # MRI license
  /Copyright(?:::)?\s+\(C\)\s+\d{4}\s+Network Applied Communication Laboratory, Inc\./, # MRI stdlibs: thread, timeout
  /\* BEGIN LICENSE BLOCK \**\s*\n\s*\*\s*Version: EPL 2\.0\/GPL 2\.0\/LGPL 2\.1/,
  /#+\s*BEGIN LICENSE BLOCK\s*#+\s*\n\s*#\s*Version: EPL 2\.0\/GPL 2\.0\/LGPL 2\.1/,
  /This file is part of ruby-ffi\./,
  /This is a public domain/,
]

truffle_paths = %w[
  lib/cext/include/truffleruby
  lib/cext/preprocess.rb
  lib/truffle
  src
  test/truffle
  tool/generate
  spec/truffle
  LICENCE.md
  README.md
  doc/legal/legal.md
] + [__FILE__]

excludes = %w[
  lib/cext/include/truffleruby/config_
  lib/cext/include/truffleruby/internal
  lib/truffle/date
  lib/truffle/ffi
  lib/truffle/io/console/size.rb
  lib/truffle/pathname
  lib/truffle/securerandom
  lib/truffle/strscan
  src/main/c/bigdecimal
  src/main/c/date
  src/main/c/etc
  src/main/c/io-console
  src/main/c/json
  src/main/c/nkf
  src/main/c/openssl
  src/main/c/prism-gem
  src/main/c/psych
  src/main/c/rbconfig-sizeof
  src/main/c/syslog
  src/main/c/yarp
  src/main/c/zlib
  src/yarp
  spec/truffle/fixtures/hello-world
  test/truffle/pack-real-usage.rb
  test/truffle/cexts
  test/truffle/ecosystem
  test/truffle/integration/backtraces/fixtures
]

excluded_files = %w[
  extconf.rb
]

truffle_paths.each do |path|
  next if %w[tool/generate].include?(path)
  abort "WARNING: incorrect path #{path}" unless File.exist? path
end

paths = `git ls-files`.lines(chomp: true)
paths = paths.select { |path|
  EXTENSIONS.include?(File.extname(path)) &&
    truffle_paths.any? { |prefix| path.start_with? prefix } &&
    excludes.none? { |prefix| path.start_with? prefix } &&
    !excluded_files.include?(File.basename(path)) &&
    File.exist?(path) &&
    File.readlines(path).size > 5
}

paths.each do |file|
  ext = File.extname(file)
  md = ext == '.md'
  header = File.read(file, *(400 unless md))

  copyright_regexp = COPYRIGHT

  unless copyright_regexp =~ header
    next if md
    if OTHER_COPYRIGHTS.none? { |copyright| copyright =~ header }
      puts "Adding copyright in #{file}"
      File.write(file, NEW_COPYRIGHT[ext]+File.read(file))
    end
    next
  end

  next if ENV["ADD_ONLY"]

  copyright, year1, year2, comma, holder = $~[:copyright], $~[:year1], $~[:year2], $~[:comma], $~[:holder]
  year1 = Integer(year1)
  year2 = Integer(year2 || year1)

  if year > year2
    contents = File.read(file)
    years = "#{year1}-#{year}"
    contents.sub!(copyright_regexp, "#{copyright} (c) #{years}#{comma} #{holder}")
    File.write(file, contents)

    puts "Updated year in #{file}"
  end
end
