# -*- encoding: utf-8 -*-
# stub: prism 1.5.1 ruby lib
# stub: ext/prism/extconf.rb

Gem::Specification.new do |s|
  s.name = "prism".freeze
  s.version = "1.5.1".freeze

  s.required_rubygems_version = Gem::Requirement.new(">= 0".freeze) if s.respond_to? :required_rubygems_version=
  s.metadata = { "allowed_push_host" => "https://rubygems.org", "changelog_uri" => "https://github.com/ruby/prism/blob/main/CHANGELOG.md", "source_code_uri" => "https://github.com/ruby/prism" } if s.respond_to? :metadata=
  s.require_paths = ["lib".freeze]
  s.authors = ["Shopify".freeze]
  s.date = "2025-10-08"
  s.email = ["ruby@shopify.com".freeze]
  s.extensions = ["ext/prism/extconf.rb".freeze]
  s.files = ["ext/prism/extconf.rb".freeze, "prism.rb".freeze, "prism/compiler.rb".freeze, "prism/desugar_compiler.rb".freeze, "prism/dispatcher.rb".freeze, "prism/dsl.rb".freeze, "prism/ffi.rb".freeze, "prism/inspect_visitor.rb".freeze, "prism/lex_compat.rb".freeze, "prism/mutation_compiler.rb".freeze, "prism/node.rb".freeze, "prism/node_ext.rb".freeze, "prism/pack.rb".freeze, "prism/parse_result.rb".freeze, "prism/parse_result/comments.rb".freeze, "prism/parse_result/errors.rb".freeze, "prism/parse_result/newlines.rb".freeze, "prism/pattern.rb".freeze, "prism/polyfill/append_as_bytes.rb".freeze, "prism/polyfill/byteindex.rb".freeze, "prism/polyfill/scan_byte.rb".freeze, "prism/polyfill/unpack1.rb".freeze, "prism/polyfill/warn.rb".freeze, "prism/reflection.rb".freeze, "prism/relocation.rb".freeze, "prism/serialize.rb".freeze, "prism/string_query.rb".freeze, "prism/translation.rb".freeze, "prism/translation/parser.rb".freeze, "prism/translation/parser/builder.rb".freeze, "prism/translation/parser/compiler.rb".freeze, "prism/translation/parser/lexer.rb".freeze, "prism/translation/parser33.rb".freeze, "prism/translation/parser34.rb".freeze, "prism/translation/parser35.rb".freeze, "prism/translation/parser_current.rb".freeze, "prism/translation/ripper.rb".freeze, "prism/translation/ripper/sexp.rb".freeze, "prism/translation/ripper/shim.rb".freeze, "prism/translation/ruby_parser.rb".freeze, "prism/visitor.rb".freeze]
  s.homepage = "https://github.com/ruby/prism".freeze
  s.licenses = ["MIT".freeze]
  s.required_ruby_version = Gem::Requirement.new(">= 2.7.0".freeze)
  s.rubygems_version = "3.6.9".freeze
  s.summary = "Prism Ruby parser".freeze
end
