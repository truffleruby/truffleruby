# -*- encoding: utf-8 -*-
# stub: io-nonblock 0.3.2 ruby lib
# stub: ext/io/nonblock/extconf.rb

Gem::Specification.new do |s|
  s.name = "io-nonblock".freeze
  s.version = "0.3.2".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "homepage_uri" => "https://github.com/ruby/io-nonblock", "source_code_uri" => "https://github.com/ruby/io-nonblock" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Nobu Nakada".freeze]
  s.date = "2026-03-17"
  s.description = "Enables non-blocking mode with IO class".freeze
  s.email = ["nobu@ruby-lang.org".freeze]
  s.extensions = ["ext/io/nonblock/extconf.rb".freeze]
  s.files = ["COPYING".freeze, "README.md".freeze, "ext/io/nonblock/depend".freeze, "ext/io/nonblock/extconf.rb".freeze, "ext/io/nonblock/nonblock.c".freeze, "io/nonblock.#{Truffle::Platform::DLEXT}".freeze]
  s.homepage = "https://github.com/ruby/io-nonblock".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 3.0".freeze)
  s.rubygems_version = "4.0.6".freeze
  s.summary = "Enables non-blocking mode with IO class".freeze
end
