# -*- encoding: utf-8 -*-
# stub: zlib 3.2.3 ruby lib
# stub: ext/zlib/extconf.rb

Gem::Specification.new do |s|
  s.name = "zlib".freeze
  s.version = "3.2.3".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Yukihiro Matsumoto".freeze, "UENO Katsuhiro".freeze]
  s.bindir = "exe".freeze
  s.date = "2026-03-11"
  s.description = "Ruby interface for the zlib compression/decompression library".freeze
  s.email = ["matz@ruby-lang.org".freeze, nil]
  s.extensions = ["ext/zlib/extconf.rb".freeze]
  s.files = ["ext/zlib/extconf.rb".freeze, "zlib.#{Truffle::Platform::DLEXT}".freeze]
  s.homepage = "https://github.com/ruby/zlib".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.5.0".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Ruby interface for the zlib compression/decompression library".freeze
end
