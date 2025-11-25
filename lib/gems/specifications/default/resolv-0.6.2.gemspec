# -*- encoding: utf-8 -*-
# stub: resolv 0.6.2 ruby lib
# stub: ext/win32/resolv/extconf.rb

Gem::Specification.new do |s|
  s.name = "resolv".freeze
  s.version = "0.6.2".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "homepage_uri" => "https://github.com/ruby/resolv", "source_code_uri" => "https://github.com/ruby/resolv" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Tanaka Akira".freeze]
  s.bindir = "exe".freeze
  s.date = "2025-10-08"
  s.description = "Thread-aware DNS resolver library in Ruby.".freeze
  s.email = ["akr@fsij.org".freeze]
  s.extensions = ["ext/win32/resolv/extconf.rb".freeze]
  s.files = ["ext/win32/resolv/extconf.rb".freeze, "resolv.rb".freeze]
  s.homepage = "https://github.com/ruby/resolv".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.3.0".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Thread-aware DNS resolver library in Ruby.".freeze
end
