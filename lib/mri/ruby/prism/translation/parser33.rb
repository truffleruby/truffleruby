# frozen_string_literal: true
# :markup: markdown

module Ruby::Prism
  module Translation
    # This class is the entry-point for Ruby 3.3 of `Ruby::Prism::Translation::Parser`.
    class Parser33 < Parser
      def version # :nodoc:
        33
      end
    end
  end
end
