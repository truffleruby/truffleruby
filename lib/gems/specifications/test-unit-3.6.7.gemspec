# -*- encoding: utf-8 -*-
# stub: test-unit 3.6.7 ruby lib

Gem::Specification.new do |s|
  s.name = "test-unit".freeze
  s.version = "3.6.7".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "bug_tracker_uri" => "https://github.com/test-unit/test-unit/issues", "documentation_uri" => "https://test-unit.github.io/test-unit/en/", "source_code_uri" => "https://github.com/test-unit/test-unit" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Kouhei Sutou".freeze, "Haruka Yoshihara".freeze]
  s.date = "2025-10-08"
  s.description = "test-unit (Test::Unit) is unit testing framework for Ruby, based on xUnit\nprinciples. These were originally designed by Kent Beck, creator of extreme\nprogramming software development methodology, for Smalltalk's SUnit. It allows\nwriting tests, checking results and automated testing in Ruby.".freeze
  s.email = ["kou@cozmixng.org".freeze, "yoshihara@clear-code.com".freeze]
  s.homepage = "https://test-unit.github.io/".freeze
  s.licenses = ["Ruby".freeze, "BSDL".freeze, "PSFL".freeze]
  s.rubygems_version = "3.4.20".freeze
  s.summary = "An xUnit family unit testing framework for Ruby.".freeze

  s.installed_by_version = "3.6.9".freeze

  s.specification_version = 4

  s.add_runtime_dependency(%q<power_assert>.freeze, [">= 0".freeze])
end
