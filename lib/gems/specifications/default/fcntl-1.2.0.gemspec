# -*- encoding: utf-8 -*-
# stub: fcntl 1.2.0 ruby lib
# stub: ext/fcntl/extconf.rb

Gem::Specification.new do |s|
  s.name = "fcntl".freeze
  s.version = "1.2.0".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.require_paths = ["lib".freeze]
  s.authors = ["Yukihiro Matsumoto".freeze]
  s.bindir = "exe".freeze
  s.date = "2025-10-08"
  s.description = "Loads constants defined in the OS fcntl.h C header file".freeze
  s.email = ["matz@ruby-lang.org".freeze]
  s.extensions = ["ext/fcntl/extconf.rb".freeze]
  s.extra_rdoc_files = [".document".freeze, ".rdoc_options".freeze, "BSDL".freeze, "COPYING".freeze, "README.md".freeze]
  s.files = [".document".freeze, ".rdoc_options".freeze, "BSDL".freeze, "COPYING".freeze, "README.md".freeze, "ext/fcntl/extconf.rb".freeze, "fcntl.#{Truffle::Platform::DLEXT}".freeze]
  s.homepage = "https://github.com/ruby/fcntl".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.5.0".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Loads constants defined in the OS fcntl.h C header file".freeze
end
