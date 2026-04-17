# -*- encoding: utf-8 -*-
# stub: date 3.5.1 ruby lib
# stub: ext/date/extconf.rb

Gem::Specification.new do |s|
  s.name = "date".freeze
  s.version = "3.5.1".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "changelog_uri" => "https://github.com/ruby/date/releases" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Tadayoshi Funaba".freeze]
  s.date = "2026-03-17"
  s.description = "The official date library for Ruby.".freeze
  s.email = [nil]
  s.extensions = ["ext/date/extconf.rb".freeze]
  s.files = ["BSDL".freeze, "COPYING".freeze, "README.md".freeze, "date_core.#{Truffle::Platform::DLEXT}".freeze, "ext/date/date_core.c".freeze, "ext/date/date_parse.c".freeze, "ext/date/date_strftime.c".freeze, "ext/date/date_strptime.c".freeze, "ext/date/date_tmx.h".freeze, "ext/date/extconf.rb".freeze, "ext/date/prereq.mk".freeze, "ext/date/zonetab.h".freeze, "ext/date/zonetab.list".freeze, "lib/date.rb".freeze, "lib/lib/date.rb".freeze]
  s.homepage = "https://github.com/ruby/date".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.6.0".freeze)
  s.rubygems_version = "4.0.6".freeze
  s.summary = "The official date library for Ruby.".freeze
end
