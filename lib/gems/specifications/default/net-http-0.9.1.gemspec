# -*- encoding: utf-8 -*-
# stub: net-http 0.9.1 ruby lib

Gem::Specification.new do |s|
  s.name = "net-http".freeze
  s.version = "0.9.1".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "changelog_uri" => "https://github.com/ruby/net-http/releases", "homepage_uri" => "https://github.com/ruby/net-http", "source_code_uri" => "https://github.com/ruby/net-http" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["NARUSE, Yui".freeze]
  s.bindir = "exe".freeze
  s.date = "2026-03-17"
  s.description = "HTTP client api for Ruby.".freeze
  s.email = ["naruse@airemix.jp".freeze]
  s.files = ["lib/net/http.rb".freeze, "lib/net/http/exceptions.rb".freeze, "lib/net/http/generic_request.rb".freeze, "lib/net/http/header.rb".freeze, "lib/net/http/proxy_delta.rb".freeze, "lib/net/http/request.rb".freeze, "lib/net/http/requests.rb".freeze, "lib/net/http/response.rb".freeze, "lib/net/http/responses.rb".freeze, "lib/net/http/status.rb".freeze, "lib/net/https.rb".freeze]
  s.homepage = "https://github.com/ruby/net-http".freeze
  s.licenses = ["Ruby".freeze, "BSD-2-Clause".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.7.0".freeze)
  s.rubygems_version = "4.0.6".freeze
  s.summary = "HTTP client api for Ruby.".freeze

  s.specification_version = 4

  s.add_runtime_dependency(%q<uri>.freeze, [">= 0.11.1".freeze])
end
