# frozen_string_literal: true

module Prism
  module Translation
    class Ripper
      class Lexer < Ripper
        def scan(raise_errors: false)
          result = Prism.lex_compat(@source, filepath: filename, line: lineno)

          if result.failure? && raise_errors
            raise SyntaxError, result.errors.first.message
          else
            result.value.to_a.map do |position, event, token, state|
              Elem.new(position, event, token, state.to_int)
            end
          end
        end

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

        class Elem
          attr_accessor :pos, :event, :tok, :state, :message

          def initialize(pos, event, tok, state, message = nil)
            @pos = pos
            @event = event
            @tok = tok
            @state = State.new(state)
            @message = message
          end

          def [](index)
            case index
            when 0, :pos
              @pos
            when 1, :event
              @event
            when 2, :tok
              @tok
            when 3, :state
              @state
            when 4, :message
              @message
            else
              nil
            end
          end

          def inspect
            "#<#{self.class}: #{event}@#{pos[0]}:#{pos[1]}:#{state}: #{tok.inspect}#{": " if message}#{message}>"
          end

          alias to_s inspect

          def pretty_print(q)
            q.group(2, "#<#{self.class}:", ">") {
              q.breakable
              q.text("#{event}@#{pos[0]}:#{pos[1]}")
              q.breakable
              state.pretty_print(q)
              q.breakable
              q.text("token: ")
              tok.pretty_print(q)
              if message
                q.breakable
                q.text("message: ")
                q.text(message)
              end
            }
          end

          def to_a
            if @message
              [@pos, @event, @tok, @state, @message]
            else
              [@pos, @event, @tok, @state]
            end
          end
        end
      end
    end
  end
end