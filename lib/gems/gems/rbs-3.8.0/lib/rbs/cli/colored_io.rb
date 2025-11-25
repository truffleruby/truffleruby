# frozen_string_literal: true

module RBS
  class CLI
    class ColoredIO
      attr_reader :stdout

      def initialize(stdout:)
        @stdout = stdout
      end

      def puts_red(string)
        if can_display_colors?
          puts "\e[31m#{string}\e[m"
        else
          puts string
        end
      end

      def puts_green(string)
        if can_display_colors?
          puts "\e[32m#{string}\e[m"
        else
          puts string
        end
      end

      def puts(...)
        stdout.puts(...)
      end

      private

      # https://github.com/rubygems/rubygems/blob/ed65279100234a17d65d71fe26de5083984ac5b8/bundler/lib/bundler/vendor/thor/lib/thor/shell/color.rb#L99-L109
      def can_display_colors?
        are_colors_supported? && !are_colors_disabled?
      end

      def are_colors_supported?
        stdout.tty? && ENV["TERM"] != "dumb"
      end

      def are_colors_disabled?
        !ENV['NO_COLOR'].nil? && !ENV.fetch('NO_COLOR', '').empty?
      end
    end
  end
end
