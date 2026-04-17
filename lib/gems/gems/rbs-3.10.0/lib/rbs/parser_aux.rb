# frozen_string_literal: true

require_relative "parser/lex_result"
require_relative "parser/token"

module RBS
  class Parser
    def self.parse_type(source, range: 0..., variables: [], require_eof: false)
      buf = buffer(source)
      _parse_type(buf, range.begin || 0, range.end || buf.last_position, variables, require_eof)
    end

    def self.parse_method_type(source, range: 0..., variables: [], require_eof: false)
      buf = buffer(source)
      _parse_method_type(buf, range.begin || 0, range.end || buf.last_position, variables, require_eof)
    end

    def self.parse_signature(source)
      buf = buffer(source)

      resolved = magic_comment(buf)
      start_pos =
        if resolved
          (resolved.location || raise).end_pos
        else
          0
        end
      dirs, decls = _parse_signature(buf, start_pos, buf.last_position)

      if resolved
        dirs = dirs.dup if dirs.frozen?
        dirs.unshift(resolved)
      end

      [buf, dirs, decls]
    end

    def self.parse_type_params(source, module_type_params: true)
      buf = buffer(source)
      _parse_type_params(buf, 0, buf.last_position, module_type_params)
    end

    def self.magic_comment(buf)
      start_pos = 0

      while true
        case
        when match = /\A#\s*(?<keyword>resolve-type-names)\s*(?<colon>:)\s+(?<value>true|false)$/.match(buf.content, start_pos)
          value = match[:value] or raise

          kw_offset = match.offset(:keyword) #: [Integer, Integer]
          colon_offset = match.offset(:colon) #: [Integer, Integer]
          value_offset = match.offset(:value) #: [Integer, Integer]

          location = Location.new(buf, kw_offset[0], value_offset[1])
          location.add_required_child(:keyword, kw_offset[0]...kw_offset[1])
          location.add_required_child(:colon, colon_offset[0]...colon_offset[1])
          location.add_required_child(:value, value_offset[0]...value_offset[1])

          return AST::Directives::ResolveTypeNames.new(value: value == "true", location: location)
        else
          return
        end
      end
    end

    def self.lex(source)
      buf = buffer(source)
      list = _lex(buf, buf.last_position)
      value = list.map do |type, location|
        Token.new(type: type, location: location)
      end
      LexResult.new(buffer: buf, value: value)
    end

    def self.buffer(source)
      case source
      when String
        Buffer.new(content: source, name: "a.rbs")
      when Buffer
        source
      end
    end

    KEYWORDS = %w(
      bool
      bot
      class
      instance
      interface
      nil
      self
      singleton
      top
      void
      type
      unchecked
      in
      out
      end
      def
      include
      extend
      prepend
      alias
      module
      attr_reader
      attr_writer
      attr_accessor
      public
      private
      untyped
      true
      false
      ).each_with_object({}) do |keyword, hash| #$ Hash[String, bot]
        hash[keyword] = _ = nil
      end
  end
end
