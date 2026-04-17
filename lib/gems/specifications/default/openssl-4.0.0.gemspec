# -*- encoding: utf-8 -*-
# stub: openssl 4.0.0 ruby lib
# stub: ext/openssl/extconf.rb

Gem::Specification.new do |s|
  s.name = "openssl".freeze
  s.version = "4.0.0".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "msys2_mingw_dependencies" => "openssl" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Martin Bosslet".freeze, "SHIBATA Hiroshi".freeze, "Zachary Scott".freeze, "Kazuki Yamaguchi".freeze]
  s.date = "2026-03-17"
  s.description = "OpenSSL for Ruby provides access to SSL/TLS and general-purpose cryptography based on the OpenSSL library.".freeze
  s.email = ["ruby-core@ruby-lang.org".freeze]
  s.extensions = ["ext/openssl/extconf.rb".freeze]
  s.extra_rdoc_files = ["CONTRIBUTING.md".freeze, "NEWS.md".freeze, "README.ja.md".freeze, "README.md".freeze]
  s.files = ["BSDL".freeze, "CONTRIBUTING.md".freeze, "COPYING".freeze, "History.md".freeze, "NEWS.md".freeze, "README.ja.md".freeze, "README.md".freeze, "ext/openssl/extconf.rb".freeze, "lib/lib/openssl.rb".freeze, "lib/lib/openssl/bn.rb".freeze, "lib/lib/openssl/buffering.rb".freeze, "lib/lib/openssl/cipher.rb".freeze, "lib/lib/openssl/digest.rb".freeze, "lib/lib/openssl/hmac.rb".freeze, "lib/lib/openssl/marshal.rb".freeze, "lib/lib/openssl/pkcs5.rb".freeze, "lib/lib/openssl/pkey.rb".freeze, "lib/lib/openssl/ssl.rb".freeze, "lib/lib/openssl/version.rb".freeze, "lib/lib/openssl/x509.rb".freeze, "lib/openssl.rb".freeze, "lib/openssl/bn.rb".freeze, "lib/openssl/buffering.rb".freeze, "lib/openssl/cipher.rb".freeze, "lib/openssl/digest.rb".freeze, "lib/openssl/hmac.rb".freeze, "lib/openssl/marshal.rb".freeze, "lib/openssl/pkcs5.rb".freeze, "lib/openssl/pkey.rb".freeze, "lib/openssl/ssl.rb".freeze, "lib/openssl/version.rb".freeze, "lib/openssl/x509.rb".freeze, "openssl.#{Truffle::Platform::DLEXT}".freeze]
  s.homepage = "https://github.com/ruby/openssl".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.rdoc_options = ["--main".freeze, "README.md".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.7.0".freeze)
  s.rubygems_version = "4.0.6".freeze
  s.summary = "SSL/TLS and general-purpose cryptography for Ruby".freeze
end
