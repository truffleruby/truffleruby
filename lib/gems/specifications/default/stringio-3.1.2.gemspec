# -*- encoding: utf-8 -*-
# stub: stringio 3.1.2 ruby lib
# stub: ext/stringio/extconf.rb

Gem::Specification.new do |s|
  s.name = "stringio".freeze
  s.version = "3.1.2".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Nobu Nakada".freeze, "Charles Oliver Nutter".freeze]
  s.date = "2025-10-08"
  s.description = "Pseudo `IO` class from/to `String`.".freeze
  s.email = ["nobu@ruby-lang.org".freeze, "headius@headius.com".freeze]
  s.extensions = ["ext/stringio/extconf.rb".freeze]
  s.extra_rdoc_files = [".document".freeze, ".rdoc_options".freeze, "COPYING".freeze, "LICENSE.txt".freeze, "NEWS.md".freeze, "README.md".freeze, "docs/io.rb".freeze, "ext/stringio/.document".freeze]
  s.files = [".document".freeze, ".rdoc_options".freeze, "COPYING".freeze, "LICENSE.txt".freeze, "NEWS.md".freeze, "README.md".freeze, "docs/io.rb".freeze, "ext/stringio/.document".freeze, "ext/stringio/extconf.rb".freeze, "stringio.#{Truffle::Platform::DLEXT}".freeze]
  s.homepage = "https://github.com/ruby/stringio".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.7".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Pseudo IO on String".freeze
end
