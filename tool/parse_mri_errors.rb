#!/usr/bin/env ruby
## Experimental MRI test tagging script from a test run log
## The input is read from stdin or the first argument

# Usage:
#     tool/parse_mri_errors.rb output.txt
# or
#     jt test mri test/mri/tests/rdoc/test_rdoc_token_stream.rb | tool/parse_mri_errors.rb
require 'etc'
require 'fileutils'

REASON = ENV['REASON']
SLOW_TEST_THRESHOLD = 30
VERY_SLOW_TEST_THRESHOLD = 60
REPO_ROOT = File.expand_path("../..", __FILE__)
EXCLUDES_DIR = "#{REPO_ROOT}/test/mri/excludes"
RETRY_EXIT_STATUS = 2

# Usually the first line in the error message gives us enough context to quickly identify what caused the failure.
# Sometimes, the first line isn't helpful and we need to look further down. This filter helps us discard unhelpful data.
def should_skip_error?(message)
  Regexp.union(
    /\[ruby-\w+:\d+\]/i,   # MRI bug IDs.
    /\[Bug #?\d+\]/i,      # MRI bug IDs.
    /\[bug:\d+\]/i,        # MRI bug IDs.
    /pid \d+ exit \d/i,    # PID and exit status upon failure.
  ).match(message)
end

def platform_info
  if RUBY_PLATFORM.include?('darwin')
    cpu_model = `sysctl -n machdep.cpu.brand_string`.strip
  elsif RUBY_PLATFORM.include?('linux')
    cpu_model = `cat /proc/cpuinfo | grep 'model name' | uniq`.split(':').last.strip
  else
    cpu_model = 'unknown'
  end

  "#{cpu_model}: ( #{Etc.nprocessors} vCPUs)"
end

def exclude_test!(class_name, test_method, error_display)
  file = EXCLUDES_DIR + "/" + class_name.split("::").join('/') + ".rb"

  # If a method name breaks the rules for method naming, e.g. it's defined dynamically with #define_method,
  # in the tests output it's wrapped into "...". The patterns may not remove `""` so do this explicitly.
  if test_method[0] == '"' && test_method[-1] == '"'
    test_method = test_method[1..-2]
  end

  # A method name can contain escape sequences e.g. \t, \v etc (when a method is created
  # with #define_method method call). Such method name is escaped when being printed in
  # the test output. So it should be unescaped to match an actual Ruby method name.
  #
  # Example:
  #   output contains:
  #     '[ 1/13] Prism::MagicCommentTest#"test_magic_comment_#  \t\v encoding  \t\v :  \t\v ascii  \t\v"dyld[88292]: missing symbol called'
  #   captured method name:
  #     'test_magic_comment_#  \t\v encoding  \t\v :  \t\v ascii  \t\v'
  #   unescaped (with #undump):
  #     "test_magic_comment_#  \t\v encoding  \t\v :  \t\v ascii  \t\v"
  name_dumped = test_method
  name_undumped = ('"'+test_method+'"').undump
  prefix = "exclude #{name_undumped.to_sym.inspect}," # don't strip a method name as far as some test names are defined with #define_method and contain terminating whitespaces

  new_line = "#{prefix} #{(REASON || error_display).inspect}\n"

  FileUtils.mkdir_p(File.dirname(file))
  lines = File.exist?(file) ? File.readlines(file) : []

  # we need the ',' to handle a case when one test name is a substring of another test name
  if i = lines.index { |line| line.start_with?(prefix) }
    puts "already excluded: #{class_name}##{test_method}"
    lines[i] = new_line
  else
    puts "adding exclude: #{class_name}##{test_method}"
    lines << new_line
  end

  # The test method name in the tag may have characters that can't appear in a symbol literal without quoting. However,
  # the presence of the quotation marks causes the sorting to look a little funny. Such tags will appear at the front
  # of the list because of lexicographic ordering. However, we'd prefer to have an alphabetic ordering based on the
  # name of the method. So, we extract the name from each symbol and sort them that way.
  lines.sort_by! { |line| line.match(/^exclude :"?(.*?)"?,/)[1] }

  File.write(file, lines.join)
end

module Patterns
  # A pattern for a method name is a bit complicated and isn't as simple as `\w+`.
  # By convention a method can be terminated with '?' or '!' character. Moreover
  # dynamically defined methods can terminate with any non-space character/characters,
  # e.g. with '>>', '==' or '[]'.
  #
  # Examples:
  # - TestBignum_BigZero#test_zero?
  # - TestRDocCrossReference#"test_resolve_method:!" (generated with `define_method`)
  # - Prism::MagicCommentTest#"test_magic_comment_#  \t\v encoding  \t\v :  \t\v ascii  \t\v"
  #
  # In case a method name contains characters that don't allowed by a parser a method in the output is wrapped with "".
  # So the pattern should look like `#"?\w+[^"\n]*"?`

  METHOD_NAME = /\w+[?!]?|"\w+[^"\n]*?"/

  # Sample:
  #
  # [101/125] TestM17N#test_string_inspect_encoding
  # truffleruby: an internal exception escaped out of the interpreter,
  # please report it to https://github.com/truffleruby/truffleruby/issues
  #
  # ```
  # <no message> (java.lang.AssertionError)
  # 	from org.truffleruby.core.string.StringNodes$CharacterPrintablePrimitiveNode.isCharacterPrintable(StringNodes.java:3102)
  #
  # Extracts: ['TestM17N', 'test_string_inspect_encoding', '<no message> (java.lang.AssertionError) from org.truffleruby.core.string.StringNodes$CharacterPrintablePrimitiveNode.isCharacterPrintable(StringNodes.java:3102)']
  ESCAPED_EXCEPTION = /^\[\s*\d+\/\d+\] ((?:\w+::)*\w+)#(#{METHOD_NAME})\n.*?```\n(.*?\n.*?)\n/m

  # Sample:
  #
  # [ 7/13] TestNum2int#test_num2ll#
  # # A fatal error has been detected by the Java Runtime Environment:
  # #
  # #  SIGSEGV (0xb) at pc=0x00000001049c29b8, pid=68349, tid=6151
  # #
  # # JRE version: OpenJDK Runtime Environment GraalVM CE 24-dev+11.1 (24.0+11) (build 24+11-jvmci-b01)
  # # Java VM: OpenJDK 64-Bit Server VM GraalVM CE 24-dev+11.1 (24+11-jvmci-b01, mixed mode, sharing, tiered, jvmci, jvmci compiler, compressed oops, compressed class ptrs, g1 gc, bsd-aarch64)
  # # Problematic frame:
  # # V  [libjvm.dylib+0x9be9b8]  Unsafe_GetDouble(JNIEnv_*, _jobject*, _jobject*, long)+0x13c
  #
  # Extracts: ['TestNum2int', 'test_num2ll', 'SIGSEGV', 'V  [libjvm.dylib+0x9be9b8]  Unsafe_GetDouble(JNIEnv_*, _jobject*, _jobject*, long)+0x13c']
  JVM_CRASH = /^\[\s*\d+\/\d+\] ((?:\w+::)*\w+)#(#{METHOD_NAME})\s*#\n# A fatal error has been detected.*?(?:\n#)+\s+(SIG\w+).*?Problematic frame:\n#\s+(.+?)\n/m

  # Sample: [19/21] TestSH#test_strftimetest/mri/tests/runner.rb: TestSH#test_strftime: symbol lookup error: /home/nirvdrum/dev/workspaces/truffleruby-ws/truffleruby/mxbuild/truffleruby-jvm-ce/lib/mri/date_core.so: undefined symbol: rb_str_format
  # Extracts: ['TestSH', 'test_strftimetest', 'rb_str_format']
  #
  # Sample: [1/3] TestBignum_Big2str#test_big2str_generictest/mri/tests/runner.rb: TestBignum_Big2str#test_big2str_generic: symbol lookup error: /home/nirvdrum/dev/workspaces/truffleruby-ws/truffleruby/.ext/c/bignum.so: undefined symbol: rb_big2str_generic
  # Extracts: ['TestBignum_Big2str', 'test_big2str', 'rb_big2str_generic']
  #
  # Sample: test/mri/tests/runner.rb: TestRDocParserRuby#test_read_directive_one_liner: symbol lookup error: /b/b/e/main/mxbuild/truffleruby-native/lib/mri/ripper.so: undefined symbol: rb_parser_st_locale_insensitive_strncasecmp
  # Extracts: ['TestRDocParserRuby', 'test_read_directive_one_liner', 'rb_parser_st_locale_insensitive_strncasecmp']
  MISSING_SYMBOL = / ((?:\w+::)*\w+)#(#{METHOD_NAME}): symbol lookup error.*? undefined symbol: (\w+)/

  # Sample:  [1/4] TestThreadInstrumentation#test_join/home/nirvdrum/dev/workspaces/truffleruby-ws/truffleruby/mxbuild/truffleruby-jvm-ce/bin/ruby: symbol lookup error: /home/nirvdrum/dev/workspaces/truffleruby-ws/truffleruby/.ext/c/thread/instrumentation.so: undefined symbol: rb_internal_thread_add_event_hook
  # Extracts: ['TestThreadInstrumentation', 'test_join', 'rb_internal_thread_add_event_hook']
  SYMBOL_LOOKUP_ERROR = / ((?:\w+::)*\w+)#(#{METHOD_NAME})(?:\/.*?)?: symbol lookup error.*? undefined symbol: (\w+)/

  # Sample: [ 35/123] TestFileExhaustive#test_expand_path_hfsdyld[32447]: missing symbol called
  # Extracts: ['TestFileExhaustive', 'test_expand_path_hfs', 'missing symbol called']
  DYLD_MISSING_SYMBOL = / ((?:\w+::)*\w+)#(#{METHOD_NAME})dyld\[\d+\]: (.*)/

  # Sample: [ 6/39] TestSocket_UNIXSocket#test_addr = 0.02 s
  # Extracts: ['TestSocket_UNIXSocket', 'test_addr', '0.02']
  TEST_EXECUTION_TIME = /^\[\s*\d+\/\d+\] ((?:\w+::)*\w+)#(#{METHOD_NAME}) = (\d+\.\d+) s/

  # Too many examples to list. Take a look at the generated mri_tests.txt file when you use `jt retag`.
  # NOTE: method names containing whitespaces, special characters etc aren't wrapped with "".
  TEST_FAILURE = /^\s+\d+\) (Error|Failure|Timeout):\n((?:\w+::)*\w+)#(.+?)(?:\s*\[(?:[^\]])+\])?:?\n(.*?)\n$/m
end

def process_fatal_errors!(contents)
  # If we have an exception escape the interpreter, it will have caused the TruffleRuby process to abort. The test
  # results will likely be truncated. We'll attempt to figure out what test was running when the exception occurred
  # and tag it. Then we'll let the caller know that they should retry tagging by exiting with a particular status code.
  contents.scan(Patterns::ESCAPED_EXCEPTION) do |class_name, test_method, exception_message|
    exclude_test!(class_name, test_method, exception_message.sub(/\n\s+/, ' '))

    exit RETRY_EXIT_STATUS
  end

  # In rare cases a bug in TruffleRuby or GraalVM can result in the process crashing. We'll attempt to parse out the
  # signal type and the problematic frame to write the exclusion message, and then retry tagging so any other tests
  # have the opportunity to run.
  contents.scan(Patterns::JVM_CRASH) do |class_name, test_method, error_type, source|
    exclude_test!(class_name, test_method, "JVM crash; #{error_type}: #{source}")

    exit RETRY_EXIT_STATUS
  end

  # If we're running a test that relies on a C symbol we haven't implemented, the process will exit informing us that
  # the symbol was undefined. We want to parse that out, tag the associated test, and then retry tagging so any other
  # tests have the opportunity to run.
  contents.scan(Patterns::MISSING_SYMBOL) do |class_name, test_method, missing_symbol|
    exclude_test!(class_name, test_method, "undefined symbol: #{missing_symbol}")

    exit RETRY_EXIT_STATUS
  end

  # In some cases the output format is a little different when an undefined symbol is encountered. We treat this as a
  # separate case to keep the regular expression simpler. Since this could overlap with the other symbol lookup error
  # case, this one must appear second. Otherwise, the extracted method could be incorrect.
  #
  # In this case, the interpreter path is printed instead of the path to the runner. And it is attached to the test
  # method name with no space between them. Additionally, the method name isn't printed twice as it is when the runner
  # path is printed, so we're forced to have to extract the method name that's joined to the interpreter path3.
  # Fortunately, the path is absolute so we do have a delimiter character we can use.
  contents.scan(Patterns::SYMBOL_LOOKUP_ERROR) do |class_name, test_method, missing_symbol|
    exclude_test!(class_name, test_method, "undefined symbol: #{missing_symbol}")

    exit RETRY_EXIT_STATUS
  end

  # We've observed on macOS that encountering undefined symbols presents yet another format to parse. Unfortunately, in
  # this situation the message may not include what the symbol is. But, we can still extract the error message and tag
  # the test for reprocessing.
  contents.scan(Patterns::DYLD_MISSING_SYMBOL) do |class_name, test_method, dyld_message|
    exclude_test!(class_name, test_method, "dyld: #{dyld_message}")

    exit RETRY_EXIT_STATUS
  end
end

# Define unit tests for patterns this way. Run them every time the script is launched.
# A test failure doesn't terminate retagging but at least it helps to refactor and modify the patterns.
module PatternTests
  def self.assert(value, message)
    unless value
      raise "Assertion failure: #{message}"
    end
  end

  # ESCAPED_EXCEPTION

  output = <<-OUTPUT
[101/125] TestM17N#test_string_inspect_encoding
truffleruby: an internal exception escaped out of the interpreter,
please report it to https://github.com/truffleruby/truffleruby/issues

```
<no message> (java.lang.AssertionError)
	from org.truffleruby.core.string.StringNodes$CharacterPrintablePrimitiveNode.isCharacterPrintable(StringNodes.java:3102)
  OUTPUT
  expected = ['TestM17N', 'test_string_inspect_encoding', "<no message> (java.lang.AssertionError)\n\tfrom org.truffleruby.core.string.StringNodes$CharacterPrintablePrimitiveNode.isCharacterPrintable(StringNodes.java:3102)"]
  actual = Patterns::ESCAPED_EXCEPTION.match(output).captures
  assert(actual == expected, '[ESCAPED_EXCEPTION] captures test class, test method name, and error message')

  # JVM_CRASH

  output = <<-OUTPUT
[ 7/13] TestNum2int#test_num2ll#
# A fatal error has been detected by the Java Runtime Environment:
#
#  SIGSEGV (0xb) at pc=0x00000001049c29b8, pid=68349, tid=6151
#
# JRE version: OpenJDK Runtime Environment GraalVM CE 24-dev+11.1 (24.0+11) (build 24+11-jvmci-b01)
# Java VM: OpenJDK 64-Bit Server VM GraalVM CE 24-dev+11.1 (24+11-jvmci-b01, mixed mode, sharing, tiered, jvmci, jvmci compiler, compressed oops, compressed class ptrs, g1 gc, bsd-aarch64)
# Problematic frame:
# V  [libjvm.dylib+0x9be9b8]  Unsafe_GetDouble(JNIEnv_*, _jobject*, _jobject*, long)+0x13c
  OUTPUT
  expected = ['TestNum2int', 'test_num2ll', "SIGSEGV", "V  [libjvm.dylib+0x9be9b8]  Unsafe_GetDouble(JNIEnv_*, _jobject*, _jobject*, long)+0x13c"]
  actual = Patterns::JVM_CRASH.match(output).captures
  assert(actual == expected, '[JVM_CRASH] captures test class, test method name, error type, and source')

  # MISSING_SYMBOL

  output = 'test/mri/tests/runner.rb: TestRDocParserRuby#test_read_directive_one_liner: symbol lookup error: /b/ripper.so: undefined symbol: rb_parser_st_locale_insensitive_strncasecmp'
  expected = ['TestRDocParserRuby', 'test_read_directive_one_liner', 'rb_parser_st_locale_insensitive_strncasecmp']
  actual = Patterns::MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[MISSING_SYMBOL] captures test class, test method name, and a missing symbol')

  output = 'test/mri/tests/runner.rb: TestRDocAnyMethod#test_has_call_seq?: symbol lookup error: /b/ripper.so: undefined symbol: rb_parser_st_locale_insensitive_strncasecmp'
  expected = ['TestRDocAnyMethod', 'test_has_call_seq?', 'rb_parser_st_locale_insensitive_strncasecmp']
  actual = Patterns::MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[MISSING_SYMBOL] when method name terminated with ? captures it properly')

  output = 'test/mri/tests/runner.rb:  TestRDocAnyMethod#"test_resolve_method:!": symbol lookup error: /b/ripper.so: undefined symbol: rb_parser_st_locale_insensitive_strncasecmp'
  expected = ['TestRDocAnyMethod', '"test_resolve_method:!"', 'rb_parser_st_locale_insensitive_strncasecmp']
  actual = Patterns::MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[MISSING_SYMBOL] when method name terminated with multiple non-alphanumeric characters captures it properly')

  output = 'test/mri/tests/runner.rb: Prism::MagicCommentTest#"test_magic_comment_# encoding: ascii": symbol lookup error: /b/ripper.so: undefined symbol: rb_parser_st_locale_insensitive_strncasecmp'
  expected = ['Prism::MagicCommentTest', '"test_magic_comment_# encoding: ascii"', 'rb_parser_st_locale_insensitive_strncasecmp']
  actual = Patterns::MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[MISSING_SYMBOL] when method name contains whitespaces captures it properly')

  # SYMBOL_LOOKUP_ERROR

  output = '[1/4] TestThreadInstrumentation#test_join_counters/home/ruby: symbol lookup error: /home/instrumentation.so: undefined symbol: rb_internal_thread_add_event_hook'
  expected = ['TestThreadInstrumentation', 'test_join_counters', 'rb_internal_thread_add_event_hook']
  actual = Patterns::SYMBOL_LOOKUP_ERROR.match(output).captures
  assert(actual == expected, '[SYMBOL_LOOKUP_ERROR] captures test class, test method name, and a missing symbol')

  output = '[1/4] TestRDocAnyMethod#test_has_call_seq?/home/ruby: symbol lookup error: /home/instrumentation.so: undefined symbol: rb_internal_thread_add_event_hook'
  expected = ['TestRDocAnyMethod', 'test_has_call_seq?', 'rb_internal_thread_add_event_hook']
  actual = Patterns::SYMBOL_LOOKUP_ERROR.match(output).captures
  assert(actual == expected, '[SYMBOL_LOOKUP_ERROR] when method name terminated with ? captures it properly')

  output = '[1/4] TestRDocAnyMethod#"test_resolve_method:!"/home/ruby: symbol lookup error: /home/instrumentation.so: undefined symbol: rb_internal_thread_add_event_hook'
  expected = ['TestRDocAnyMethod', '"test_resolve_method:!"', 'rb_internal_thread_add_event_hook']
  actual = Patterns::SYMBOL_LOOKUP_ERROR.match(output).captures
  assert(actual == expected, '[SYMBOL_LOOKUP_ERROR] when method name terminated with multiple non-alphanumeric characters captures it properly')

  output = '[1/4] Prism::MagicCommentTest#"test_magic_comment_# encoding: ascii"/home/ruby: symbol lookup error: /home/instrumentation.so: undefined symbol: rb_internal_thread_add_event_hook'
  expected = ['Prism::MagicCommentTest', '"test_magic_comment_# encoding: ascii"', 'rb_internal_thread_add_event_hook']
  actual = Patterns::SYMBOL_LOOKUP_ERROR.match(output).captures
  assert(actual == expected, '[SYMBOL_LOOKUP_ERROR] when method name contains whitespaces captures it properly')

  output = '[1/4] TestRDocAnyMethod#"test_resolve_method:/"/home/ruby: symbol lookup error: /home/instrumentation.so: undefined symbol: rb_internal_thread_add_event_hook'
  expected = ['TestRDocAnyMethod', '"test_resolve_method:/"', 'rb_internal_thread_add_event_hook']
  actual = Patterns::SYMBOL_LOOKUP_ERROR.match(output).captures
  assert(actual == expected, '[SYMBOL_LOOKUP_ERROR] when method name terminated with / captures it properly')

  # DYLD_MISSING_SYMBOL

  output = '[ 35/123] TestFileExhaustive#test_expand_path_hfsdyld[32447]: missing symbol called'
  expected = ['TestFileExhaustive', 'test_expand_path_hfs', 'missing symbol called']
  actual = Patterns::DYLD_MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[DYLD_MISSING_SYMBOL] captures test class, and test method name')

  output = '[ 35/123] TestRDocAnyMethod#test_has_call_seq?dyld[32447]: missing symbol called'
  expected = ['TestRDocAnyMethod', 'test_has_call_seq?', 'missing symbol called']
  actual = Patterns::DYLD_MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[DYLD_MISSING_SYMBOL] when method name terminated with ? captures it properly')

  output = '[ 35/123] TestRDocAnyMethod#"test_resolve_method:!"dyld[32447]: missing symbol called'
  expected = ['TestRDocAnyMethod', '"test_resolve_method:!"', 'missing symbol called']
  actual = Patterns::DYLD_MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[DYLD_MISSING_SYMBOL] when method name terminated with multiple non-alphanumeric characters captures it properly')

  output = '[ 35/123] Prism::MagicCommentTest#"test_magic_comment_# encoding: ascii"dyld[32447]: missing symbol called'
  expected = ['Prism::MagicCommentTest', '"test_magic_comment_# encoding: ascii"', 'missing symbol called']
  actual = Patterns::DYLD_MISSING_SYMBOL.match(output).captures
  assert(actual == expected, '[DYLD_MISSING_SYMBOL] when method name contains whitespaces captures it properly')

  # TEST_EXECUTION_TIME

  output = '[ 6/39] TestSocket_UNIXSocket#test_addr = 0.02 s'
  expected = ['TestSocket_UNIXSocket', 'test_addr', '0.02']
  actual = Patterns::TEST_EXECUTION_TIME.match(output).captures
  assert(actual == expected, '[TEST_EXECUTION_TIME] captures test class, test method name, and seconds')

  output = '[ 6/39] TestRDocAnyMethod#test_has_call_seq? = 0.02 s'
  expected = ['TestRDocAnyMethod', 'test_has_call_seq?', '0.02']
  actual = Patterns::TEST_EXECUTION_TIME.match(output).captures
  assert(actual == expected, '[TEST_EXECUTION_TIME] when method name terminated with ? captures it properly')

  output = '[ 6/39] TestRDocAnyMethod#"test_resolve_method:!" = 0.02 s'
  expected = ['TestRDocAnyMethod', '"test_resolve_method:!"', '0.02']
  actual = Patterns::TEST_EXECUTION_TIME.match(output).captures
  assert(actual == expected, '[TEST_EXECUTION_TIME] when method name terminated with multiple non-alphanumeric characters captures it properly')

  output = '[ 6/39] Prism::MagicCommentTest#"test_magic_comment_# encoding: ascii" = 0.02 s'
  expected = ['Prism::MagicCommentTest', '"test_magic_comment_# encoding: ascii"', '0.02']
  actual = Patterns::TEST_EXECUTION_TIME.match(output).captures
  assert(actual == expected, '[TEST_EXECUTION_TIME] when method name contains whitespaces captures it properly')

  # TEST_FAILURE

  output = <<-OUTPUT
  1) Error:
TestFiberBacktrace#test_backtrace:
NoMethodError: undefined method `backtrace' for #<Fiber:0x3c8 root (created)>
    /b/b/e/main/test/mri/tests/fiber/test_backtrace.rb:7:in `test_backtrace'

Finished tests in 0.116441s, 25.7641 tests/s, 25.7641 assertions/s.
3 tests, 3 assertions, 0 failures, 3 errors, 0 skips
  OUTPUT
  expected = ['Error', 'TestFiberBacktrace', 'test_backtrace', "NoMethodError: undefined method `backtrace' for #<Fiber:0x3c8 root (created)>\n    /b/b/e/main/test/mri/tests/fiber/test_backtrace.rb:7:in `test_backtrace'"]
  actual = Patterns::TEST_FAILURE.match(output).captures
  assert(actual == expected, '[TEST_FAILURE] when error - captures failure type, test class, test method name and error message')

  output = <<-OUTPUT
  1) Failure:
Test_NotImplement#test_not_method_defined [/b/b/e/main/test/mri/tests/cext-ruby/test_notimplement.rb:28]:
Failed assertion, no message given.

Finished tests in 0.116441s, 25.7641 tests/s, 25.7641 assertions/s.
3 tests, 3 assertions, 0 failures, 3 errors, 0 skips
  OUTPUT
  expected = ['Failure', 'Test_NotImplement', 'test_not_method_defined', 'Failed assertion, no message given.']
  actual = Patterns::TEST_FAILURE.match(output).captures
  assert(actual == expected, '[TEST_FAILURE] when failure - captures failure type, test class, test method name and error message')

  output = <<-OUTPUT
  1) Failure:
Test_SPrintf#test_format_integer(% #-020.d) [/b/b/e/main/test/mri/tests/cext-ruby/test_printf.rb:153]:
rb_sprintf("% #-020.d", 2147483647).
<[" 2147483647         ", "% #-020.d"]> expected but was
<[" 0000000002147483647", "% #-020.d"]>.

Finished tests in 0.592768s, 42.1750 tests/s, 705.1663 assertions/s.
25 tests, 418 assertions, 2 failures, 0 errors, 0 skips
  OUTPUT
  expected = ['Failure', 'Test_SPrintf', 'test_format_integer(% #-020.d)', "rb_sprintf(\"% #-020.d\", 2147483647).\n<[\" 2147483647         \", \"% #-020.d\"]> expected but was\n<[\" 0000000002147483647\", \"% #-020.d\"]>."]
  actual = Patterns::TEST_FAILURE.match(output).captures
  assert(actual == expected, '[TEST_FAILURE] when failure and method name contains whitespaces - captures it properly')

  # TODO: add example for TIMEOUT:
end


def process_test_failures!(contents)
  contents.scan(Patterns::TEST_FAILURE) do |error_type, class_name, test_method, error|
    if error_type == 'Timeout'
      exclude_test!(class_name, test_method, "retain-on-retag; test timed out")
      next
    end

    error_lines = error.split("\n")
    index = 0

    while should_skip_error?(error_lines[index])
      index += 1
    end

    error_display = error_lines[index]

    # Mismatched expectations span two lines. It's much more useful if they're combined into one message.
    #
    # FIXME: A line with "expected but was" may be prefixed with additional line, e.g.
    # ```
    #   1) Failure:
    # Test_SPrintf#test_format_integer(% #-020.d) [/b/b/e/main/test/mri/tests/cext-ruby/test_printf.rb:153]:
    # rb_sprintf("% #-020.d", 2147483647).
    # <[" 2147483647         ", "% #-020.d"]> expected but was
    # <[" 0000000002147483647", "% #-020.d"]>.
    # ```
    # In this case only the first line of the message is added to the result - 'rb_sprintf("% #-020.d", 2147483647).'
    if error_display =~ /expected but was/
      index += 1
      error_display << ' ' + error_lines[index]

    # Mismatched exception messages span three lines. It's much more useful if they're combined into one message.
    elsif error_display =~ /but the message doesn't match/
      error_display << ' ' + error_lines[index + 1..].join(' ')
      index = error_lines.size

    # Handle exceptions by reading the message up until the backtrace.
    elsif error_display =~ /Exception raised:/
      while (line = error_lines[index + 1]) !~ /Backtrace:/
        index += 1
        error_display << ' ' << line.strip
      end

    # Assertion errors are more useful with the first line of the backtrace.
    elsif error_display&.include?('java.lang.AssertionError')
      index += 1
      error_display << ' ' << error_lines[index ]

    # As a catch-all, any message ending with a colon likely has more context on the next line.
    elsif error_display&.end_with?(':')
      # if an error message contains excessive new lines then it can be captured only partially
      # and a line with ":" can be the last captured one
      if index < error_lines.size - 1
        index += 1
        error_display << ' ' << error_lines[index]
      end
    end

    # Generated Markdown code blocks span multiple lines. It's much more useful if they're combined into one message.
    if error_display =~ /```/
      index += 1

      begin
        until (line = error_lines[index]) =~ /```/
          error_display << line << "\n"
          index += 1
        end

        error_display << line
      rescue
        error_display = "needs investigation; multi-line code block"
      end
    end

    if error_display.to_s.strip.empty?
      error_display = "needs investigation"
    end

    exclude_test!(class_name, test_method, error_display)
  end
end

def process_slow_tests!(contents)
  if !contents.include?('ruby -v:')
    raise "Tests output doesn't contain a line with Ruby version (that looks like 'ruby -v: ...'). Please include it to proceed further."
  end

  test_ruby_version = contents.match(/ruby -v: (.*)/)[1].strip

  contents.scan(Patterns::TEST_EXECUTION_TIME) do |class_name, test_method, execution_time|
    if execution_time.to_f > SLOW_TEST_THRESHOLD
      prefix =  execution_time.to_f > VERY_SLOW_TEST_THRESHOLD ? "very slow" : "slow"
      message = "#{prefix}: #{execution_time}s on #{test_ruby_version} with #{platform_info}"

      exclude_test!(class_name, test_method, message)
    end
  end
end

contents = ARGF.read.scrub.gsub("[ruby] WARNING StackOverflowError\n", '')
process_fatal_errors!(contents)
process_test_failures!(contents)
process_slow_tests!(contents)