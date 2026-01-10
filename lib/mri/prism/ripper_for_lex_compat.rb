# frozen_string_literal: true

module Prism
  class Ripper
    # just for the `Ripper.method(:lex).parameters.assoc(:keyrest)` check
    def self.lex(src, filename = "-", lineno = 1, raise_errors: false)
    end

    # expect lex_state to be Integer
    def self.lex_state_name(lex_state)
      names = []
      (0..lex_state.size - 1).each do |i|
        names << LEX_STATE_NAMES[i] if lex_state[i] == 1
      end

      if names.empty?
        return 'NONE'
      end

      names.join('|')
    end

    # based on src/main/c/ripper/parse.c
    LEX_STATE_NAMES = [
      "BEG",    "END",    "ENDARG", "ENDFN",  "ARG",
      "CMDARG", "MID",    "FNAME",  "DOT",    "CLASS",
      "LABEL",  "LABELED","FITEM",
    ]

    # MRI: see internal/ruby_parser.h
    EXPR_BEG      = 1 << 0
    EXPR_END      = 1 << 1
    EXPR_ENDARG   = 1 << 2
    EXPR_ENDFN    = 1 << 3
    EXPR_ARG      = 1 << 4
    EXPR_CMDARG   = 1 << 5
    EXPR_MID      = 1 << 6
    EXPR_FNAME    = 1 << 7
    EXPR_DOT      = 1 << 8
    EXPR_CLASS    = 1 << 9
    EXPR_LABEL    = 1 << 10
    EXPR_LABELED  = 1 << 11
    EXPR_FITEM    = 1 << 12
    EXPR_VALUE    = EXPR_BEG
    EXPR_BEG_ANY  = EXPR_BEG | EXPR_MID | EXPR_CLASS
    EXPR_ARG_ANY  = EXPR_ARG | EXPR_CMDARG
    EXPR_END_ANY  = EXPR_END | EXPR_ENDARG | EXPR_ENDFN
    EXPR_NONE     = 0

    def initialize(source, filename = "(ripper)", lineno = 1)
      @source = source
      @filename = filename
      @lineno = lineno
      @column = 0
      @result = nil
    end

    class Lexer < Ripper
      class State
        attr_reader :to_int, :to_s

        def initialize(i)
          @to_int = i
          @to_s = Ripper.lex_state_name(i)
          freeze
        end

        def [](index)
          case index
          when 0, :to_int
            @to_int
          when 1, :to_s
            @event
          else
            nil
          end
        end

        alias to_i to_int
        alias inspect to_s
        def pretty_print(q) q.text(to_s) end
        def ==(i) super or to_int == i end
        def &(i) self.class.new(to_int & i) end
        def |(i) self.class.new(to_int | i) end
        def allbits?(i) to_int.allbits?(i) end
        def anybits?(i) to_int.anybits?(i) end
        def nobits?(i) to_int.nobits?(i) end
      end
    end
  end
end