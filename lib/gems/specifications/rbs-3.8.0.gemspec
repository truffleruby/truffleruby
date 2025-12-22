# -*- encoding: utf-8 -*-
# stub: rbs 3.8.0 ruby lib
# stub: ext/rbs_extension/extconf.rb

Gem::Specification.new do |s|
  s.name = "rbs".freeze
  s.version = "3.8.0".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "changelog_uri" => "https://github.com/ruby/rbs/blob/master/CHANGELOG.md", "homepage_uri" => "https://github.com/ruby/rbs", "source_code_uri" => "https://github.com/ruby/rbs.git" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Soutaro Matsumoto".freeze]
  s.bindir = "exe".freeze
  s.date = "2025-10-08"
  s.description = "RBS is the language for type signatures for Ruby and standard library definitions.".freeze
  s.email = ["matsumoto@soutaro.com".freeze]
  s.executables = ["rbs".freeze]
  s.extensions = ["ext/rbs_extension/extconf.rb".freeze]
  s.files = ["exe/rbs".freeze, "ext/rbs_extension/extconf.rb".freeze]
  s.homepage = "https://github.com/ruby/rbs".freeze
  s.licenses = ["BSD-2-Clause".freeze, "Ruby".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 3.1".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Type signature for Ruby.".freeze

  s.installed_by_version = "3.6.9".freeze

  s.specification_version = 4

  s.add_runtime_dependency(%q<logger>.freeze, [">= 0".freeze])
end
