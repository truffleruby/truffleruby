# -*- encoding: utf-8 -*-
# stub: error_highlight 0.7.1 ruby lib

Gem::Specification.new do |s|
  s.name = "error_highlight".freeze
  s.version = "0.7.1".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Yusuke Endoh".freeze]
  s.date = "2026-03-17"
  s.description = "The gem enhances Exception#message by adding a short explanation where the exception is raised".freeze
  s.email = ["mame@ruby-lang.org".freeze]
  s.files = ["lib/error_highlight.rb".freeze, "lib/error_highlight/base.rb".freeze, "lib/error_highlight/core_ext.rb".freeze, "lib/error_highlight/formatter.rb".freeze, "lib/error_highlight/version.rb".freeze]
  s.homepage = "https://github.com/ruby/error_highlight".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 3.2.0".freeze)
  s.rubygems_version = "4.0.6".freeze
  s.summary = "Shows a one-line code snippet with an underline in the error backtrace".freeze
end
