# frozen_string_literal: true

module RBS
  class Parser
    class Token
      attr_reader :type
      attr_reader :location

      def initialize(type:, location:)
        @type = type
        @location = location
      end

      def value
        @location.source
      end

      def comment?
        @type == :tCOMMENT || @type == :tLINECOMMENT
      end
    end
  end
end
