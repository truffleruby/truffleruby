# frozen_string_literal: true

module RBS
  class Parser
    class LexResult
      attr_reader :buffer
      attr_reader :value

      def initialize(buffer:, value:)
        @buffer = buffer
        @value = value
      end
    end
  end
end
