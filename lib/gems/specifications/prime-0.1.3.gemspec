# -*- encoding: utf-8 -*-
# stub: prime 0.1.3 ruby lib

Gem::Specification.new do |s|
  s.name = "prime".freeze
  s.version = "0.1.3".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Marc-Andre Lafortune".freeze]
  s.bindir = "exe".freeze
  s.date = "2025-10-08"
  s.description = "Prime numbers and factorization library.".freeze
  s.email = ["ruby-core@marc-andre.ca".freeze]
  s.homepage = "https://github.com/ruby/prime".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.5.0".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Prime numbers and factorization library.".freeze

  s.installed_by_version = "3.6.9".freeze

  s.specification_version = 4

  s.add_runtime_dependency(%q<singleton>.freeze, [">= 0".freeze])
  s.add_runtime_dependency(%q<forwardable>.freeze, [">= 0".freeze])
end
