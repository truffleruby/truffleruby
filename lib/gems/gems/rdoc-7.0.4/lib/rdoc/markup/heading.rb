# frozen_string_literal: true

module RDoc
  class Markup
    # A heading with a level (1-6) and text
    #
    #  RDoc syntax:
    #   = Heading 1
    #   == Heading 2
    #   === Heading 3
    #
    #  Markdown syntax:
    #   # Heading 1
    #   ## Heading 2
    #   ### Heading 3
    class Heading < Element
      #: String
      attr_reader :text

      #: Integer
      attr_accessor :level

      # A singleton RDoc::Markup::ToLabel formatter for headings.
      #: () -> RDoc::Markup::ToLabel
      def self.to_label
        @to_label ||= Markup::ToLabel.new
      end

      # A singleton plain HTML formatter for headings. Used for creating labels for the Table of Contents
      #: () -> RDoc::Markup::ToHtml
      def self.to_html
        @to_html ||= begin
          markup = Markup.new
          markup.add_regexp_handling CrossReference::CROSSREF_REGEXP, :CROSSREF

          to_html = Markup::ToHtml.new nil

          def to_html.handle_regexp_CROSSREF(target)
            target.text.sub(/^\\/, '')
          end

          to_html
        end
      end

      #: (Integer, String) -> void
      def initialize(level, text)
        super()

        @level = level
        @text = text
      end

      #: (Object) -> bool
      def ==(other)
        other.is_a?(Heading) && other.level == @level && other.text == @text
      end

      # @override
      #: (untyped) -> void
      def accept(visitor)
        visitor.accept_heading(self)
      end

      # An HTML-safe anchor reference for this header.
      #: () -> String
      def aref
        "label-#{self.class.to_label.convert text.dup}"
      end

      # Creates a fully-qualified label which will include the label from +context+. This helps keep ids unique in HTML.
      #: (RDoc::Context?) -> String
      def label(context = nil)
        label = +""
        label << "#{context.aref}-" if context&.respond_to?(:aref)
        label << aref
        label
      end

      # HTML markup of the text of this label without the surrounding header element.
      #: () -> String
      def plain_html
        no_image_text = text

        if matched = no_image_text.match(/rdoc-image:[^:]+:(.*)/)
          no_image_text = matched[1]
        end

        self.class.to_html.to_html(no_image_text)
      end

      # @override
      #: (PP) -> void
      def pretty_print(q)
        q.group 2, "[head: #{level} ", ']' do
          q.pp text
        end
      end
    end
  end
end
