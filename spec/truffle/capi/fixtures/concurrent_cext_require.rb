# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

data = File.binread(__FILE__ )

cexts = {
  etc: -> { Etc.getpwnam(Etc.getlogin) },
  nkf: -> { NKF.guess(data) },
  openssl: -> { OpenSSL::Digest::SHA256.new.digest(data) },
  :'rbconfig/sizeof' => -> { RbConfig::SIZEOF["long"] },
  syslog: -> { Syslog.open("test").close },
  yaml: -> { Psych.dump(data) },
  zlib: -> { Zlib::Deflate.deflate(data) },
}

$go = false
q = Queue.new

threads = cexts.each_pair.map do |cext, code|
  Thread.new do
    q << :ready
    Thread.pass until $go
    require cext.to_s
    100.times { code.call }
    :success
  end
end

threads.each { q.pop }
$go = true
threads.each(&:join)

puts 'success'
