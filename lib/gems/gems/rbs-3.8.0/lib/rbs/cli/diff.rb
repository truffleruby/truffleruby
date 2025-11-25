# frozen_string_literal: true

module RBS
  class CLI
    class Diff
      def initialize(argv:, library_options:, stdout: $stdout, stderr: $stderr)
        @format = nil
        @stdout = stdout
        @stderr = stderr

        # @type var type_name: String?
        type_name = nil
        library_options = library_options
        before_path = [] #: Array[String]
        after_path = [] #: Array[String]
        detail = false

        opt = OptionParser.new do |o|
          o.banner = <<~HELP
            [Experimental] This command is experimental. API and output compatibility is not guaranteed.

            Usage:
              rbs diff --format markdown --type-name Foo --before before_sig --after after_sig

            Print diff for rbs environment dir

            Examples:

              # Diff dir1 and dir2 for Foo
              $ rbs diff --format markdown --type-name Foo --before dir1 --after dir2

              # Confirmation of methods related to Time class added by including stdlib/time
              $ rbs diff --format diff --type-name Time --after stdlib/time
          HELP
          o.on("--format NAME")    { |arg| @format = arg }
          o.on("--type-name NAME") { |arg| type_name = arg }
          o.on("--before DIR")     { |arg| before_path << arg }
          o.on("--after DIR")      { |arg| after_path << arg }
          o.on("--[no-]detail")    { |arg| detail = arg }
        end
        opt.parse!(argv)

        unless @format && type_name && ["markdown", "diff"].include?(@format)
          @stderr.puts opt.banner
          exit 1
        end

        @diff = RBS::Diff.new(
          type_name: TypeName.parse(type_name).absolute!,
          library_options: library_options,
          after_path: after_path,
          before_path: before_path,
          detail: detail,
        )
      end

      def run
        public_send("run_#{@format}")
      end

      def run_diff
        first = true
        io = RBS::CLI::ColoredIO.new(stdout: @stdout)
        @diff.each_diff do |before, after|
          io.puts if !first
          io.puts_red   "- #{before}"
          io.puts_green "+ #{after}"
          first = false
        end
      end

      def run_markdown
        @stdout.puts "| before | after |"
        @stdout.puts "| --- | --- |"
        @diff.each_diff do |before, after|
          before.gsub!("|", "\\|")
          after.gsub!("|", "\\|")
          @stdout.puts "| `#{before}` | `#{after}` |"
        end
      end
    end
  end
end
