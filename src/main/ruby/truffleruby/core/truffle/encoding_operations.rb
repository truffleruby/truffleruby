# frozen_string_literal: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2019-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

module Truffle
  module EncodingOperations
    # A map with three kinds of entries:
    # * An original encoding:
    #   name.upcase.to_sym => [nil, encoding]
    # * An alias of an original encoding:
    #   alias_name.upcase.to_sym => [alias_name, original_encoding]
    # * An unset default encoding:
    #   name.upcase.to_sym => [name, nil]
    EncodingMap = {}

    def self.build_encoding_map
      Encoding.list.each do |encoding|
        key = encoding.name.upcase.to_sym
        EncodingMap[key] = [nil, encoding]
      end

      Primitive.encoding_each_alias -> alias_name, encoding do
        key = alias_name.upcase.to_sym
        EncodingMap[key] = [alias_name, encoding]
      end
    end

    def self.setup_default_encoding(name, key)
      enc = Primitive.encoding_get_default_encoding name
      EncodingMap[key] = [name, enc]
      enc
    end

    def self.change_default_encoding(name, obj)
      raise unless Primitive.is_a?(obj, Encoding) || Primitive.nil?(obj)
      key = name.upcase.to_sym
      EncodingMap[key][1] = obj
    end

    def self.dummy_encoding(name)
      new_encoding, index = Primitive.encoding_create_dummy name
      EncodingMap[name.upcase.to_sym] = [nil, new_encoding]
      [new_encoding, index]
    end

    def self.define_alias(encoding, alias_name)
      key = alias_name.upcase.to_sym
      EncodingMap[key] = [alias_name, encoding]
      Primitive.encoding_define_alias(encoding, key)
    end

    def self.name_for_inspect(encoding)
      if encoding == Encoding::BINARY
        'BINARY (ASCII-8BIT)'
      else
        encoding.name
      end
    end

    def self.transcode(string, from_enc, to_enc, **options)
      ec = Encoding::Converter.new from_enc, to_enc, **options
      dest = +''
      src = string.dup
      fallback = options[:fallback]
      status = ec.primitive_convert src, dest, nil, nil
      while status != :finished
        raise ec.last_error unless fallback && status == :undefined_conversion
        (_, fallback_enc_from, fallback_enc_to, error_bytes, _) = ec.primitive_errinfo
        rep = fallback[error_bytes.force_encoding(fallback_enc_from)]
        raise ec.last_error unless rep
        rep = Primitive.convert_with_to_str(rep)
        dest << rep.encode(fallback_enc_to)
        status = ec.primitive_convert src, dest, nil, nil
      end

      dest
    end

    def self.handle_encode_options(string, from_enc, invalid: nil, replace: nil, xml: nil, universal_newline: nil, **)
      if invalid == :replace
        replacement = replace || (Primitive.encoding_is_unicode?(from_enc) ? "\ufffd" : '?')
        string.scrub!(replacement)
      end

      case xml
      when :text
        string.gsub!(/[&><]/, '&' => '&amp;', '>' => '&gt;', '<' => '&lt;')
      when :attr
        string.gsub!(/[&><"]/, '&' => '&amp;', '>' => '&gt;', '<' => '&lt;', '"' => '&quot;')
        string.insert(0, '"')
        string.insert(-1, '"')
      when nil
        # nothing
      else
        raise ArgumentError, "unexpected value for xml option: #{xml.inspect}"
      end

      if universal_newline
        string.gsub!(/\r\n|\r/, "\r\n" => "\n", "\r" => "\n")
      end

      string
    end
  end
end
