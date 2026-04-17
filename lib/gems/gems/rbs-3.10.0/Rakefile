require "bundler/gem_tasks"
require "rake/testtask"
require "rbconfig"
require 'rake/extensiontask'

$LOAD_PATH << File.join(__dir__, "test")

ruby = ENV["RUBY"] || RbConfig.ruby
rbs = File.join(__dir__, "exe/rbs")
bin = File.join(__dir__, "bin")

Rake::ExtensionTask.new("rbs_extension")

compile_task = Rake::Task[:compile]

task :setup_extconf_compile_commands_json do
  ENV["COMPILE_COMMANDS_JSON"] = "1"
end

compile_task.prerequisites.unshift(:setup_extconf_compile_commands_json)

test_config = lambda do |t|
  t.libs << "test"
  t.libs << "lib"
  t.test_files = FileList["test/**/*_test.rb"].reject do |path|
    path =~ %r{test/stdlib/}
  end
  if defined?(RubyMemcheck)
    if t.is_a?(RubyMemcheck::TestTask)
      t.verbose = true
      t.options = '-v'
    end
  end
end

Rake::TestTask.new(test: :compile, &test_config)

unless Gem.win_platform?
  begin
    require "ruby_memcheck"

    namespace :test do
      RubyMemcheck::TestTask.new(valgrind: :compile, &test_config)
    end
  rescue LoadError => exn
    STDERR.puts "🚨🚨🚨🚨 Skipping RubyMemcheck: #{exn.inspect} 🚨🚨🚨🚨"
  end
end

multitask :default => [:test, :stdlib_test, :typecheck_test, :rubocop, :validate, :test_doc]

task :lexer do
  sh "re2c -W --no-generation-date -o src/lexer.c src/lexer.re"
  sh "clang-format -i -style=file src/lexer.c"
end

task :confirm_lexer => :lexer do
  puts "Testing if lexer.c is updated with respect to lexer.re"
  sh "git diff --exit-code src/lexer.c"
end

task :confirm_templates => :templates do
  puts "Testing if generated code under include and src is updated with respect to templates"
  sh "git diff --exit-code -- include src"
end

# Task to format C code using clang-format
namespace :format do
  dirs = ["src", "ext", "include"]

  # Find all C source and header files
  files = `find #{dirs.join(" ")} -type f \\( -name "*.c" -o -name "*.h" \\)`.split("\n")

  desc "Format C source files using clang-format"
  task :c do
    puts "Formatting C files..."

    # Check if clang-format is installed
    unless system("which clang-format > /dev/null 2>&1")
      abort "Error: clang-format not found. Please install clang-format first."
    end

    if files.empty?
      puts "No C files found to format"
      next
    end

    puts "Found #{files.length} files to format (excluding generated files)"

    exit_status = 0
    files.each do |file|
      puts "Formatting #{file}"
      unless system("clang-format -i -style=file #{file}")
        puts "❌ Error formatting #{file}"
        exit_status = 1
      end
    end

    exit exit_status unless exit_status == 0
    puts "✅ All files formatted successfully"
  end

  desc "Check if C source files are properly formatted"
  task :c_check do
    puts "Checking C file formatting..."

    # Check if clang-format is installed
    unless system("which clang-format > /dev/null 2>&1")
      abort "Error: clang-format not found. Please install clang-format first."
    end

    if files.empty?
      puts "No C files found to check"
      next
    end

    puts "Found #{files.length} files to check (excluding generated files)"

    needs_format = false
    files.each do |file|
      formatted = `clang-format -style=file #{file}`
      original = File.read(file)

      if formatted != original
        puts "❌ #{file} needs formatting"
        puts "Diff:"
        # Save formatted version to temp file and run diff
        temp_file = "#{file}.formatted"
        File.write(temp_file, formatted)
        system("diff -u #{file} #{temp_file}")
        File.unlink(temp_file)
        needs_format = true
      end
    end

    if needs_format
      warn "Some files need formatting. Run 'rake format:c' to format them."
      exit 1
    else
      puts "✅ All files are properly formatted"
    end
  end
end

rule ".c" => ".re" do |t|
  puts "⚠️⚠️⚠️ #{t.name} is older than #{t.source}. You may need to run `rake lexer` ⚠️⚠️⚠️"
end

rule %r{^src/(.*)\.c} => 'templates/%X.c.erb' do |t|
  puts "⚠️⚠️⚠️ #{t.name} is older than #{t.source}. You may need to run `rake templates` ⚠️⚠️⚠️"
end
rule %r{^include/(.*)\.c} => 'templates/%X.c.erb' do |t|
  puts "⚠️⚠️⚠️ #{t.name} is older than #{t.source}. You may need to run `rake templates` ⚠️⚠️⚠️"
end

task :annotate do
  sh "bin/generate_docs.sh"
end

task :confirm_annotation do
  puts "Testing if RBS docs are updated with respect to RDoc"
  sh "git diff --exit-code core stdlib"
end

task :templates do
  sh "#{ruby} templates/template.rb ext/rbs_extension/ast_translation.h"
  sh "#{ruby} templates/template.rb ext/rbs_extension/ast_translation.c"

  sh "#{ruby} templates/template.rb ext/rbs_extension/class_constants.h"
  sh "#{ruby} templates/template.rb ext/rbs_extension/class_constants.c"

  sh "#{ruby} templates/template.rb include/rbs/ast.h"
  sh "#{ruby} templates/template.rb src/ast.c"

  # Format the generated files
  Rake::Task["format:c"].invoke
end

task :compile => "ext/rbs_extension/class_constants.h"
task :compile => "ext/rbs_extension/class_constants.c"
task :compile => "src/lexer.c"

task :test_doc do
  files = Dir.chdir(File.expand_path('..', __FILE__)) do
    `git ls-files -z`.split("\x0").select do |file| Pathname(file).extname == ".md" end
  end

  sh "#{ruby} #{__dir__}/bin/run_in_md.rb #{files.join(" ")}"
end

task :validate => :compile do
  require 'yaml'

  sh "#{ruby} #{rbs} validate --exit-error-on-syntax-error"

  libs = FileList["stdlib/*"].map {|path| File.basename(path).to_s }

  # Skip RBS validation because Ruby CI runs without rubygems
  case skip_rbs_validation = ENV["SKIP_RBS_VALIDATION"]
  when nil
    begin
      Gem::Specification.find_by_name("rbs")
      libs << "rbs"
    rescue Gem::MissingSpecError
      STDERR.puts "🚨🚨🚨🚨 Skipping `rbs` gem because it's not found"
    end
  when "true"
    # Skip
  else
    STDERR.puts "🚨🚨🚨🚨 SKIP_RBS_VALIDATION is expected to be `true` or unset, given `#{skip_rbs_validation}` 🚨🚨🚨🚨"
    libs << "rbs"
  end

  libs.each do |lib|
    args = ["-r", lib]

    if lib == "rbs"
      args << "-r"
      args << "prism"
    end

    sh "#{ruby} #{rbs} #{args.join(' ')} validate --exit-error-on-syntax-error"
  end
end

FileList["test/stdlib/**/*_test.rb"].each do |test|
  task test => :compile do
    sh "#{ruby} -Ilib #{bin}/test_runner.rb #{test}"
  end
end

task :stdlib_test => :compile do
  test_files = FileList["test/stdlib/**/*_test.rb"].reject do |path|
    path =~ %r{Ractor} || path =~ %r{Encoding} || path =~ %r{CGI-escape_test}
  end

  if ENV["RANDOMIZE_STDLIB_TEST_ORDER"] == "true"
    test_files.shuffle!
  end

  sh "#{ruby} -Ilib #{bin}/test_runner.rb #{test_files.join(' ')}"
  # TODO: Ractor tests need to be run in a separate process
  sh "#{ruby} -Ilib #{bin}/test_runner.rb test/stdlib/CGI-escape_test.rb"
  sh "#{ruby} -Ilib #{bin}/test_runner.rb test/stdlib/Ractor_test.rb"
  sh "#{ruby} -Ilib #{bin}/test_runner.rb test/stdlib/Encoding_test.rb"
end

task :typecheck_test => :compile do
  Bundler.with_unbundled_env do
    FileList["test/typecheck/*"].each do |test|
      Dir.chdir(test) do
        expectations = File.join(test, "steep_expectations.yml")
        if File.exist?(expectations)
          sh "#{__dir__}/bin/steep check --with_expectations"
        else
          sh "#{__dir__}/bin/steep check"
        end
      end
    end
  end
end

task :raap => :compile do
  sh "ruby test/raap/core.rb"
  sh "ruby test/raap/digest.rb"
  sh "ruby test/raap/openssl.rb"
end

task :rubocop do
  format = if ENV["CI"]
    "github"
  else
    "progress"
  end

  sh "rubocop --parallel --format #{format}"
end

namespace :generate do
  desc "Generate a test file for a stdlib class signatures"
  task :stdlib_test, [:class] do |_task, args|
    klass = args.fetch(:class) do
      raise "Class name is necessary. e.g. rake 'generate:stdlib_test[String]'"
    end

    require "erb"
    require "rbs"

    class TestTarget
      def initialize(klass)
        @type_name = RBS::Namespace.parse(klass).to_type_name
      end

      def path
        Pathname(ENV['RBS_GENERATE_TEST_PATH'] || "test/stdlib/#{file_name}_test.rb")
      end

      def file_name
        @type_name.to_s.gsub(/\A::/, '').gsub(/::/, '_')
      end

      def to_s
        @type_name.to_s
      end

      def absolute_type_name
        @absolute_type_name ||= @type_name.absolute!
      end
    end

    target = TestTarget.new(klass)
    path = target.path
    raise "#{path} already exists!" if path.exist?

    class TestTemplateBuilder
      attr_reader :target, :env

      def initialize(target)
        @target = target

        loader = RBS::EnvironmentLoader.new
        Dir['stdlib/*'].each do |lib|
          next if lib.end_with?('builtin')

          loader.add(library: File.basename(lib))
        end
        @env = RBS::Environment.from_loader(loader).resolve_type_names
      end

      def call
        ERB.new(<<~ERB, trim_mode: "-").result(binding)
          require_relative "test_helper"

          <%- unless class_methods.empty? -%>
          class <%= target %>SingletonTest < Test::Unit::TestCase
            include TestHelper

            # library "logger", "securerandom"     # Declare library signatures to load
            testing "singleton(::<%= target %>)"

          <%- class_methods.each do |method_name, definition| -%>
            def test_<%= test_name_for(method_name) %>
          <%- definition.method_types.each do |method_type| -%>
              assert_send_type "<%= method_type %>",
                               <%= target %>, :<%= method_name %>
          <%- end -%>
            end

          <%- end -%>
          end
          <%- end -%>

          <%- unless instance_methods.empty? -%>
          class <%= target %>Test < Test::Unit::TestCase
            include TestHelper

            # library "logger", "securerandom"     # Declare library signatures to load
            testing "::<%= target %>"

          <%- instance_methods.each do |method_name, definition| -%>
            def test_<%= test_name_for(method_name) %>
          <%- definition.method_types.each do |method_type| -%>
              assert_send_type "<%= method_type %>",
                               <%= target %>.new, :<%= method_name %>
          <%- end -%>
            end

          <%- end -%>
          end
          <%- end -%>
        ERB
      end

      private

      def test_name_for(method_name)
        {
          :==  => 'double_equal',
          :!=  => 'not_equal',
          :=== => 'triple_equal',
          :[]  => 'square_bracket',
          :[]= => 'square_bracket_assign',
          :>   => 'greater_than',
          :<   => 'less_than',
          :>=  => 'greater_than_equal_to',
          :<=  => 'less_than_equal_to',
          :<=> => 'spaceship',
          :+   => 'plus',
          :-   => 'minus',
          :*   => 'multiply',
          :/   => 'divide',
          :**  => 'power',
          :%   => 'modulus',
          :&   => 'and',
          :|   => 'or',
          :^   => 'xor',
          :>>  => 'right_shift',
          :<<  => 'left_shift',
          :=~  => 'pattern_match',
          :!~  => 'does_not_match',
          :~   => 'tilde'
        }.fetch(method_name, method_name)
      end

      def class_methods
        @class_methods ||= RBS::DefinitionBuilder.new(env: env).build_singleton(target.absolute_type_name).methods.select {|_, definition|
          definition.implemented_in == target.absolute_type_name
        }
      end

      def instance_methods
        @instance_methods ||= RBS::DefinitionBuilder.new(env: env).build_instance(target.absolute_type_name).methods.select {|_, definition|
          definition.implemented_in == target.absolute_type_name
        }
      end
    end

    path.write TestTemplateBuilder.new(target).call

    puts "Created: #{path}"
  end
end

task :test_generate_stdlib do
  sh "RBS_GENERATE_TEST_PATH=/tmp/Array_test.rb rake 'generate:stdlib_test[Array]'"
  sh "ruby -c /tmp/Array_test.rb"
  sh "RBS_GENERATE_TEST_PATH=/tmp/Thread_Mutex_test.rb rake 'generate:stdlib_test[Thread::Mutex]'"
  sh "ruby -c /tmp/Thread_Mutex_test.rb"
end

Rake::Task[:release].enhance do
  Rake::Task[:"release:note"].invoke
end

namespace :release do
  desc "Explain the post-release steps automatically"
  task :note do
    version = Gem::Version.new(RBS::VERSION)
    major, minor, patch, *_ = RBS::VERSION.split(".")
    major = major.to_i
    minor = minor.to_i
    patch = patch.to_i

    puts "🎉🎉🎉🎉 Congratulations for **#{version}** release! 🎉🎉🎉🎉"
    puts
    puts "There are a few things left to complete the release. 💪"
    puts

    if patch == 0 || version.prerelease?
      puts "* [ ] Update release note: https://github.com/ruby/rbs/wiki/Release-Note-#{major}.#{minor}"
    end

    if patch == 0 && !version.prerelease?
      puts "* [ ] Delete `RBS XYZ is the latest version of...` from release note: https://github.com/ruby/rbs/wiki/Release-Note-#{major}.#{minor}"
    end

    puts "* [ ] Publish a release at GitHub"
    puts "* [ ] Make some announcements on Twitter/Mustdon/Slack/???"

    puts
    puts

    puts "✏️ Making a draft release on GitHub..."

    content = File.read(File.join(__dir__, "CHANGELOG.md"))
    changelog = content.scan(/^## \d.*?(?=^## \d)/m)[0]
    changelog = changelog.sub(/^.*\n^.*\n/, "").rstrip

    notes = <<NOTES
[Release note](https://github.com/ruby/rbs/wiki/Release-Note-#{major}.#{minor})

#{changelog}
NOTES

    command = [
      "gh",
      "release",
      "create",
      "--draft",
      "v#{RBS::VERSION}",
      "--title=#{RBS::VERSION}",
      "--notes=#{notes}"
    ]

    if version.prerelease?
      command << "--prerelease"
    end

    require "open3"
    output, status = Open3.capture2(*command)
    if status.success?
      puts "  >> Done! Open #{output.chomp} and publish the release!"
    end
  end
end


desc "Generate changelog template from GH pull requests"
task :changelog do
  major, minor, patch, _pre = RBS::VERSION.split(".", 4)
  major = major.to_i
  minor = minor.to_i
  patch = patch.to_i

  if patch == 0
    milestone = "RBS #{major}.#{minor}"
  else
    milestone = "RBS #{major}.#{minor}.x"
  end

  puts "🔍 Finding pull requests that is associated to milestone `#{milestone}`..."

  command = [
    "gh",
    "pr",
    "list",
    "--limit=10000",
    "--json",
    "url,title,number",
    "--search" ,
    "milestone:\"#{milestone}\" is:merged sort:updated-desc -label:Released"
  ]

  require "open3"
  output, status = Open3.capture2(*command)
  raise status.inspect unless status.success?

  require "json"
  json = JSON.parse(output, symbolize_names: true)

  unless json.empty?
    puts
    json.each do |line|
      puts "* #{line[:title]} ([##{line[:number]}](#{line[:url]}))"
    end
  else
    puts "  (🤑 There is no *unreleased* pull request associated to the milestone.)"
  end
end

desc "Compile extension without C23 extensions"
task :compile_c99 do
  ENV["TEST_NO_C23"] = "true"
  Rake::Task[:"compile"].invoke
ensure
  ENV.delete("TEST_NO_C23")
end

task :prepare_bench do
  ENV.delete("DEBUG")
  Rake::Task[:"clobber"].invoke
  Rake::Task[:"templates"].invoke
  Rake::Task[:"compile"].invoke
end

task :prepare_profiling do
  ENV["DEBUG"] = "1"
  Rake::Task[:"clobber"].invoke
  Rake::Task[:"templates"].invoke
  Rake::Task[:"compile"].invoke
end
