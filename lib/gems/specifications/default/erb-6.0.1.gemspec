# -*- encoding: utf-8 -*-
# stub: erb 6.0.1 ruby lib
# stub: ext/erb/escape/extconf.rb

Gem::Specification.new do |s|
  s.name = "erb".freeze
  s.version = "6.0.1".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "changelog_uri" => "https://github.com/ruby/erb/blob/v6.0.1/NEWS.md", "homepage_uri" => "https://github.com/ruby/erb", "source_code_uri" => "https://github.com/ruby/erb" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Masatoshi SEKI".freeze, "Takashi Kokubun".freeze]
  s.bindir = "libexec".freeze
  s.date = "2026-03-17"
  s.description = "An easy to use but powerful templating system for Ruby.".freeze
  s.email = ["seki@ruby-lang.org".freeze, "k0kubun@ruby-lang.org".freeze]
  s.executables = ["erb".freeze]
  s.extensions = ["ext/erb/escape/extconf.rb".freeze]
  s.files = ["erb/escape.#{Truffle::Platform::DLEXT}".freeze, "ext/erb/escape/extconf.rb".freeze, "lib/erb.rb".freeze, "lib/erb/compiler.rb".freeze, "lib/erb/def_method.rb".freeze, "lib/erb/util.rb".freeze, "lib/erb/version.rb".freeze, "libexec/erb".freeze]
  s.homepage = "https://github.com/ruby/erb".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 3.2.0".freeze)
  s.rubygems_version = "4.0.6".freeze
  s.summary = "An easy to use but powerful templating system for Ruby.".freeze
end
