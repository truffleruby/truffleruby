#!/usr/bin/env ruby
# encoding: utf-8
# frozen_string_literal: true

# Copyright (c) 2015, 2018 Oracle and/or its affiliates. All rights reserved.
# This code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# A workflow tool for TruffleRuby development

require 'fileutils'
require 'timeout'
require 'rbconfig'
require 'pathname'
autoload :JSON, 'json'
autoload :Shellwords, 'shellwords'

if RUBY_ENGINE != 'ruby' && !RUBY_DESCRIPTION.include?('Native')
  STDERR.puts 'WARNING: jt is not running on MRI or TruffleRuby Native, startup is slow'
  STDERR.puts '  Consider adding this to your `~/.bash_profile` to run jt on MRI.'
  STDERR.puts '    export SYSTEM_RUBY=/path/to/mri/bin/ruby'
  STDERR.puts '    alias jt=/path/to/truffleruby/bin/jt'
end

abort "ERROR: jt requires Ruby 2.3 and above, was #{RUBY_VERSION}" if (RUBY_VERSION.split('.').map(&:to_i) <=> [2, 3, 0]) < 0

TRUFFLERUBY_DIR = File.expand_path('../..', File.realpath(__FILE__))
GRAAL_DIR = File.expand_path('../graal', TRUFFLERUBY_DIR)
PROFILES_DIR = "#{TRUFFLERUBY_DIR}/profiles"

JDKS_CACHE_DIR = File.expand_path('~/.mx/jdks')

CACHE_EXTRA_DIR = File.expand_path('~/.mx/cache/truffleruby')
FileUtils.mkdir_p(CACHE_EXTRA_DIR)

ALL_PLATFORMS = %w[
  linux-amd64
  linux-aarch64
  darwin-amd64
  darwin-aarch64
]

JDEBUG = '--vm.agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=y'
METRICS_REPS = Integer(ENV['TRUFFLERUBY_METRICS_REPS'] || 10)
DEFAULT_PROFILE_OPTIONS = %w[--cpusampler --cpusampler.Output=flamegraph]

MRI_TEST_RETAG_RETAIN_PATTERNS = [
  /hangs/i,
  /slow/i,
  /retain-on-retag/,
  /OOM/,
  /flaky/,
  /spurious/,
  /transient/,
  /if RUBY_PLATFORM/,
  /pid \d+ (exit|killed)/,
  /GR-\d+/ # mentioning an existing ticket number
]

RUBOCOP_INCLUDE_LIST = %w[
  lib/cext
  lib/truffle
  src/main/ruby
  src/test-internal/ruby
  tool/docker.rb
  tool/jt.rb
  spec/truffle
]

SEAFOAM_VERSION = '0.17'
CFG2ASM_VERSION = '0.2'

DLEXT = RbConfig::CONFIG.fetch('DLEXT')

JT_PROFILE_SUBCOMMANDS = ENV['JT_PROFILE_SUBCOMMANDS'] == 'true'
JT_SPECS_COMPILATION = ENV['JT_SPECS_COMPILATION'] == 'false' ? false : true
JT_SPECS_SPLITTING = ENV['JT_SPECS_SPLITTING'] == 'true' ? true : JT_SPECS_COMPILATION

# Expand GEM_HOME relative to cwd so it cannot be misinterpreted later.
ENV['GEM_HOME'] = File.expand_path(ENV['GEM_HOME']) if ENV['GEM_HOME']

JDK_VERSIONS = %w[latest 21]
LTS_JDK_VERSION = '21'
IGV_JDK_VERSION = '21'
DEFAULT_JDK_VERSION = 'latest'

BOOTSTRAP_GRAALVM_VERSION = '25.0.1'

# Not yet 'jdk.graal' as we test against 21 and 21 does not know 'jdk.graal'
GRAAL_OPTION_PREFIX = 'graal'

MRI_TEST_RELATIVE_PREFIX = 'test/mri/tests'
MRI_TEST_PREFIX = "#{TRUFFLERUBY_DIR}/#{MRI_TEST_RELATIVE_PREFIX}"
MRI_TEST_CEXT_DIR = "#{MRI_TEST_PREFIX}/cext-c"
MRI_TEST_CEXT_LIB_DIR = "#{TRUFFLERUBY_DIR}/.ext/c"

# A list of MRI C API tests we can load. Tests that do not load at all are in failing.exclude.
MRI_TEST_CAPI_TESTS = File.readlines("#{TRUFFLERUBY_DIR}/test/mri/capi_tests.list").map(&:chomp)

MRI_TEST_MODULES = {
    '--openssl' => {
        help: 'include only openssl tests',
        include: openssl = ['openssl/test_*.rb'],
    },
    '--syslog' => {
        help: 'include only syslog tests',
        include: syslog = [
            'test_syslog.rb',
            'syslog/test_syslog_logger.rb'
        ]
    },
    '--cexts' => {
        help: 'run all MRI tests testing C extensions',
        include: cexts = openssl + syslog + [
            'etc/test_etc.rb',
            'nkf/test_kconv.rb',
            'nkf/test_nkf.rb',
            'zlib/test_zlib.rb',
        ]
    },
    '--capi' => {
      help: 'run all C-API MRI tests',
      include: capi = MRI_TEST_CAPI_TESTS,
    },
    '--all-sulong' => {
        help: 'run all tests requiring Sulong (C exts and C API)',
        include: all_sulong = cexts + capi
    },
    '--no-sulong' => {
        help: 'exclude all tests requiring Sulong',
        exclude: all_sulong
    },
}

SUBPROCESSES = []

# Forward signals to sub-processes, so they get notified when sending a signal to jt
[:SIGINT, :SIGTERM].each do |signal|
  trap(signal) do
    # Enables command line signals (e.g. Ctrl+C) when waiting for input if there are no subprocesses.
    raise Interrupt if SUBPROCESSES.empty?

    SUBPROCESSES.each do |pid|
      Utilities.send_signal(signal, pid)
    end
    # Keep running jt which will wait for the subprocesses termination
  end
end

module Utilities
  private

  def linux?
    @linux ||= RbConfig::CONFIG['host_os'].include?('linux')
  end

  def darwin?
    @darwin ||= RbConfig::CONFIG['host_os'].include?('darwin')
  end

  def amd64?
    @amd64 ||= RbConfig::CONFIG['host_cpu'] =~ /amd64|x86_64|x64/
  end

  def aarch64?
    @aarch64 ||= RbConfig::CONFIG['host_cpu'] =~ /arm64|aarch64/
  end

  def soext
    # RbConfig::CONFIG["SOEXT"] is not set for system ruby 2.3 in macOS CI
    RbConfig::CONFIG.fetch('SOEXT') { darwin? ? 'dylib' : 'so' }
  end

  def bold(text)
    STDOUT.tty? ? "\e[1m#{text}\e[22m" : text
  end

  def get_truffle_version(from: :suite)
    case from
    when :suite
      suite = File.read("#{TRUFFLERUBY_DIR}/mx.truffleruby/suite.py")
      raise unless /"name": "sulong",.+?"version": "(\h{40})"/m =~ suite
      $1
    when :repository
      sh('git', 'rev-parse', 'HEAD', capture: :out, no_print_cmd: true, chdir: GRAAL_DIR).chomp
    else
      raise ArgumentError, from: from
    end
  end

  def get_truffleruby_version
    suite = File.read("#{TRUFFLERUBY_DIR}/mx.truffleruby/suite.py")
    version = suite[/"version":\s*"([^"]+)\"/, 1]
    is_release = suite[/"release":\s*(True|False),/, 1]
    raise unless version && is_release
    if is_release == 'True'
      version
    else
      "#{version}-dev"
    end
  end

  def jvm_standalone_release_archive_path
    "release/truffleruby-jvm-#{get_truffleruby_version}-#{mx_platform}.tar.gz"
  end

  def native_standalone_release_archive_path
    "release/truffleruby-#{get_truffleruby_version}-#{mx_platform}.tar.gz"
  end

  def ee?
    @mx_env.include?('ee') || @ruby_name.include?('ee')
  end

  def graal_common_json
    "#{GRAAL_DIR}/common.json"
  end

  def truffleruby_common_json
    "#{TRUFFLERUBY_DIR}/common.json"
  end

  def jvmci_version
    @jvmci_version ||= begin
      sforceimports unless File.directory?(GRAAL_DIR)
      common_json = File.read(graal_common_json)
      if @jdk_version == 'latest'
        # The version after "-jvmci-" is not enough for latest, we also need the JDK version
        regex = /"labsjdk-ce-#{@jdk_version}":\s*\{\s*"name":\s*"labsjdk"\s*,\s*"version":\s*"(?:ce|ee)-([^"]+-jvmci-[^"]+)"\s*,/
      else
        regex = /"labsjdk-ce-#{@jdk_version}":\s*\{\s*"name":\s*"labsjdk"\s*,\s*"version":\s*"[^"]+-(jvmci-[^"]+)"\s*,/
      end
      raise "JVMCI version not found for labsjdk-ce-#{@jdk_version} in #{graal_common_json}" unless regex =~ common_json
      $1
    end
  end

  def send_signal(signal, pid)
    STDERR.puts "\nSending #{signal} to process #{pid}"
    begin
      Process.kill(signal, pid)
    rescue Errno::ESRCH
      # Already killed
      STDERR.puts "Process #{pid} not found"
      nil
    end
  end
  module_function :send_signal

  def which(binary)
    ENV['PATH'].split(File::PATH_SEPARATOR).each do |dir|
      path = "#{dir}/#{binary}"
      return path if File.executable? path
    end
    nil
  end

  def find_mx
    if which('mx')
      'mx'
    else
      suite = File.read("#{TRUFFLERUBY_DIR}/mx.truffleruby/suite.py")
      regex = /"mxversion":\s*"([^"]+)"/
      raise 'mx version not found in mx.truffleruby/suite.py' unless regex =~ suite
      mx_version = $1
      mx_repo = find_or_clone_repo('https://github.com/graalvm/mx.git', mx_version)
      "#{mx_repo}/mx"
    end
  end

  def env_path(env)
    "#{TRUFFLERUBY_DIR}/mx.truffleruby/#{env}"
  end

  def used_ruby_is_mx_env_name?
    File.file?(env_path(@ruby_name))
  end

  def ruby_launcher
    return @ruby_launcher if defined? @ruby_launcher

    ruby_launcher = if @ruby_name == 'ruby'
                      ENV['RBENV_ROOT'] ? `rbenv which ruby`.chomp : which('ruby')
                    elsif @ruby_name.start_with?('/')
                      File.directory?(@ruby_name) ? "#{@ruby_name}/bin/ruby" : @ruby_name
                    else
                      graalvm = "#{TRUFFLERUBY_DIR}/mxbuild/truffleruby-#{@ruby_name}"
                      "#{graalvm}/bin/ruby"
                    end

    raise "The Ruby executable #{ruby_launcher} does not exist" unless File.exist?(ruby_launcher)
    raise "The Ruby executable #{ruby_launcher} is not executable" unless File.executable?(ruby_launcher)

    @ruby_launcher = ruby_launcher
    @ruby_launcher_realpath = File.realpath(ruby_launcher)

    unless @silent
      shortened_path = @ruby_launcher.sub(%r[^#{Regexp.escape TRUFFLERUBY_DIR}/], '')
      shortened_path = shortened_path.sub(%r[/bin/(ruby|truffleruby)$], '')
      tags = [*('Interpreted' if truffleruby? && !truffleruby_compiler?),
              truffleruby? ? 'TruffleRuby' : 'a Ruby',
              *('with Native' if truffleruby_native_built?),
              *('with Graal' if truffleruby_compiler?)]
      STDERR.puts "Using #{tags.join(' ')}: #{shortened_path}"
    end

    # use same ruby_launcher in subprocess jt instances
    # cannot be set while building
    ENV['RUBY_BIN'] = @ruby_launcher
    @ruby_launcher
  end
  alias_method :require_ruby_launcher!, :ruby_launcher

  def ruby_home
    require_ruby_launcher!
    File.expand_path('../..', @ruby_launcher_realpath)
  end

  def truffleruby!
    raise 'This command requires TruffleRuby.' unless truffleruby?
  end

  def truffleruby_native!
    unless truffleruby_native?
      raise "The ruby executable #{ruby_launcher} is not native or --jvm was passed"
    end
  end

  def truffleruby_compiler!
    unless truffleruby_compiler?
      raise "The ruby executable #{ruby_launcher} does not have Graal.\nUse `jt build --env jvm-ce` and `jt --use jvm-ce <command> ...`."
    end
  end

  def truffleruby?
    return @truffleruby if defined?(@truffleruby)
    # only truffleruby has sibling truffleruby executable
    @truffleruby = File.executable?(truffleruby_launcher_path)
  end

  def language_lib_path
    "#{ruby_home}/lib/librubyvm.#{soext}"
  end

  def truffleruby_native_built?
    return @truffleruby_native_built if defined?(@truffleruby_native_built)
    @truffleruby_native_built = truffleruby? && File.exist?(language_lib_path)
  end

  def truffleruby_native?
    truffleruby? && truffleruby_native_built? && !truffleruby_jvm_selected_at_runtime?
  end

  def truffleruby_jvm?
    truffleruby? && (!truffleruby_native_built? || truffleruby_jvm_selected_at_runtime?)
  end

  def truffleruby_jvm_selected_at_runtime?
    ENV['TRUFFLERUBYOPT'].to_s =~ /--jvm\b/ or
      (defined?(@truffleruby_jvm_arg) and @truffleruby_jvm_arg)
  end

  def truffleruby_compiler?
    return @truffleruby_compiler if defined?(@truffleruby_compiler)

    return @truffleruby_compiler = false unless truffleruby?
    return @truffleruby_compiler = true if truffleruby_native_built?

    # Detect if the compiler is present by reading the $ruby_home/release file
    @truffleruby_compiler = File.readlines("#{ruby_home}/release").grep(/^COMMIT_INFO=/).any? do |line|
      line.include?('"compiler":') || line.include?("'compiler':")
    end
  end

  def truffleruby_launcher_path
    require_ruby_launcher!
    @truffleruby_launcher_path ||= File.expand_path('../truffleruby', @ruby_launcher_realpath)
  end

  def find_or_clone_repo(url, commit = nil)
    name = File.basename url, '.git'
    path = File.expand_path("../#{name}", TRUFFLERUBY_DIR)
    unless Dir.exist? path
      sh 'git', 'clone', url, path
      sh 'git', 'checkout', commit, chdir: path if commit
    end
    path
  end

  def git_commit
    @git_commit ||= `GIT_DIR="#{TRUFFLERUBY_DIR}/.git" git rev-parse HEAD`.strip
  end

  def git_branch
    @git_branch ||= `GIT_DIR="#{TRUFFLERUBY_DIR}/.git" git rev-parse --abbrev-ref HEAD`.strip
  end

  def no_gem_vars_env
    {
      'GEM_HOME' => nil,
      'GEM_PATH' => nil,
    }
  end

  def ruby_running_jt_env
    ruby_running_jt = RbConfig.ruby
    bin = File.dirname(ruby_running_jt)
    if which('ruby') == ruby_running_jt
      {}
    else
      { 'PATH' => "#{bin}:#{ENV['PATH']}" }
    end
  end

  def human_size(bytes)
    if bytes < 1024
      "#{bytes} B"
    elsif bytes < 1000**2
      "#{(bytes/1024.0).round(2)} KB"
    elsif bytes < 1000**3
      "#{(bytes/1024.0**2).round(2)} MB"
    elsif bytes < 1000**4
      "#{(bytes/1024.0**3).round(2)} GB"
    else
      "#{(bytes/1024.0**4).round(2)} TB"
    end
  end

  def log(tty_message, full_message)
    if STDERR.tty?
      STDERR.print tty_message unless tty_message.nil?
    else
      STDERR.print full_message unless full_message.nil?
    end
  end

  def diff(expected, actual)
    `diff -u #{expected} #{actual}`
  end

  def sh_failed_status
    `false`
    $?
  end

  def terminate_process(pid, timeout = 10)
    send_signal(:SIGTERM, pid)
    begin
      Timeout.timeout(timeout) do
        Process.wait pid
      end
    rescue Timeout::Error
      send_signal(:SIGKILL, pid)
      Process.wait pid
    end
    STDERR.puts "Process #{pid} terminated"
  end

  def sh_with_timeout(timeout, pid)
    if !timeout
      yield
    else
      begin
        Timeout.timeout(timeout) do
          yield
        end
      rescue Timeout::Error
        if send_signal(:SIGTERM, pid)
          sleep 1
          send_signal(:SIGKILL, pid)
        end
        yield # Wait and read the pipe if :capture
        :timeout
      end
    end
  end

  def sh_track_subprocess(pid)
    SUBPROCESSES << pid
    yield
  ensure
    SUBPROCESSES.delete(pid)
  end

  def sh(*args)
    options = args.last.is_a?(Hash) ? args.last : {}
    continue_on_failure = options.delete :continue_on_failure
    use_exec = options.delete :use_exec
    timeout = options.delete :timeout
    capture = options.delete :capture
    no_print_cmd = options.delete(:no_print_cmd) || @silent

    unless no_print_cmd
      STDERR.puts bold "$ #{printable_cmd(args)}"
    end

    exec(*args) if use_exec

    if capture
      capture_modes = [:out, :err, :both]
      raise ":capture can only be one of #{capture_modes}" unless capture_modes.include?(capture)
      pipe_r, pipe_w = IO.pipe
      options[:out] = pipe_w if capture == :out || capture == :both
      options[:err] = pipe_w if capture == :err || capture == :both
    end

    status = nil
    out = nil
    start = Process.clock_gettime(Process::CLOCK_MONOTONIC) if JT_PROFILE_SUBCOMMANDS
    begin
      pid = Process.spawn(*args)
    rescue Errno::ENOENT => no_such_executable
      STDERR.puts bold no_such_executable
      status = sh_failed_status
    else
      sh_track_subprocess(pid) do
        pipe_w.close if capture

        result = sh_with_timeout(timeout, pid) do
          out = pipe_r.read if capture
          _, status = Process.waitpid2(pid)
        end
        if result == :timeout
          status = sh_failed_status
        end
      end
    end

    if JT_PROFILE_SUBCOMMANDS
      finish = Process.clock_gettime(Process::CLOCK_MONOTONIC)
      STDERR.puts "\n[jt] #{printable_cmd(args)} took #{'%.3f' % (finish - start)}s\n\n"
    end

    if capture
      pipe_r.close
    end

    if status.success? || continue_on_failure
      $LAST_SH_STATUS = status

      if capture
        out
      else
        status.success?
      end
    else
      STDERR.puts "FAILED (#{status}): #{printable_cmd(args)}"
      STDERR.puts out if capture
      exit(status.exitstatus || status.termsig || status.stopsig || 1)
    end
  end

  def printable_cmd(args)
    env = {}
    if Hash === args.first
      env, *args = args
    end
    if Hash === args.last && args.last.empty?
      *args, _options = args
    end

    raise 'use multiple arguments instead of a single string with spaces' if args[0].include?(' ')
    sets = []
    unsets = []
    env.each_pair do |k, v|
      if v
        sets << "#{k}=#{shellescape(v)}"
      else
        unsets << k
      end
    end
    args = args.map { |a| shellescape(a) }

    all = [*("unset #{unsets.join(' ')};" unless unsets.empty?), *sets, *args]
    size = all.reduce(0) { |s, v| s + v.size }
    all.join(size <= 180 ? ' ' : " \\\n  ")
  end

  # From Shellwords.escape but without \n and with =
  MUST_BE_ESCAPED_OR_QUOTED = /[^A-Za-z0-9_\-.,:+\/@=]/

  def shellescape(str)
    return str unless str.is_a?(String)
    if MUST_BE_ESCAPED_OR_QUOTED =~ str
      if str.empty? or str.include?("'") or str.include?("\n")
        Shellwords.escape(str) # Use Shellwords for complex cases
      else
        "'#{str}'"
      end
    else
      str
    end
  end

  def replace_env_vars(string, env = ENV)
    string.gsub(/\$([A-Z_]+)/) do
      var = $1
      abort "You need to set $#{var}" unless env[var]
      env[var]
    end
  end

  def app_open(file)
    cmd = darwin? ? 'open' : 'xdg-open'
    sh cmd, file
  end

  def chdir(dir, &block)
    raise LocalJumpError, 'no block given' unless block_given?
    if dir == Dir.pwd
      yield
    else
      STDERR.puts "$ cd #{dir}"
      ret = Dir.chdir(dir, &block)
      STDERR.puts "$ cd #{Dir.pwd}"
      ret
    end
  end

  def in_truffleruby_repo_root!
    unless File.realpath(Dir.pwd) == TRUFFLERUBY_DIR
      raise 'This command should be called at the root of the truffleruby repository'
    end
  end

  def find_java_home
    @java_home ||= begin
      mx_env = File.expand_path('~/.mx/env')
      if File.readable?(mx_env)
        if line = File.readlines(mx_env).grep(/^JAVA_HOME=/).first
          java_home = line.strip.split('=', 2).last
        end
      end
      java_home ||= ENV['JAVA_HOME']
      fix = 'Run `unset JAVA_HOME` and retry to automatically download the correct JDK.'

      if java_home
        java_home = File.realpath(java_home)
        if java_home.include?(jvmci_version)
          return :use_env_java_home
        end

        java_version_output = `#{java_home}/bin/java -version 2>&1`
        if java_version_output.include?(jvmci_version)
          :use_env_java_home
        elsif java_version_output.include?('jvmci')
          warn "warning: JAVA_HOME=#{java_home} is not the same JVMCI version as in #{graal_common_json} (#{jvmci_version}).\n#{fix}"
          :use_env_java_home
        else
          raise "$JAVA_HOME does not seem to point to a JVMCI-enabled JDK (`#{java_home}/bin/java -version` does not contain 'jvmci').\n#{fix}"
        end
      else
        install_jvmci("$JAVA_HOME is not set, downloading JDK#{@jdk_version} with JVMCI")
      end
    end
  end

  def mx(*args, java_home: find_java_home, primary_suite: nil, **options)
    mx_args = args.dup

    env = mx_args.first.is_a?(Hash) ? mx_args.shift : {}

    if java_home == :use_env_java_home
      # mx reads $JAVA_HOME
    elsif java_home == :none
      # Make sure $JAVA_HOME is not set, as a wrong value causes mx to abort
      env['JAVA_HOME'] = nil
    else
      if java_home.start_with?(JDKS_CACHE_DIR)
        mx_args.unshift '--java-home', java_home[JDKS_CACHE_DIR.size+1..-1]
      else
        mx_args.unshift '--java-home', java_home
      end
    end

    if primary_suite and Dir.pwd != primary_suite
      mx_args.unshift '-p', primary_suite
    end

    sh(env, find_mx, *mx_args, **options)
  end

  def mx_os
    if darwin?
      'darwin'
    elsif linux?
      'linux'
    else
      abort 'Unknown OS'
    end
  end

  def mx_arch
    if amd64?
      'amd64'
    elsif aarch64?
      'aarch64'
    else
      abort 'Unknown OS'
    end
  end

  def mx_platform
    "#{mx_os}-#{mx_arch}"
  end

  def run_mspec(env_vars, command = 'run', *args)
    # Pass spec/truffleruby.mspec explicitly because we also want to use it when
    # running specs on CRuby so that it finds that specs are under spec/ruby.
    mspec_args = ['spec/mspec/bin/mspec', command, '--config', 'spec/truffleruby.mspec']

    Dir.chdir(TRUFFLERUBY_DIR) do
      # always enable assertions with --ea to catch issues earlier
      ruby env_vars, '--ea', *mspec_args, '-t', ruby_launcher, *args
    end
  end

  def run_gem_or_install(name, version, *args)
    env = ruby_running_jt_env
    if Gem::Specification.find_all_by_name(name, version).empty?
      sh env, 'gem', 'install', name, '-v', version
    end
    sh env, name, "_#{version}_", *args
  end
  ruby2_keywords :run_gem_or_install if respond_to?(:ruby2_keywords, true)

  def args_split(args)
    delimiter_index = args.index('--')
    return [args, []] unless delimiter_index
    [args[0...delimiter_index], args[(delimiter_index + 1)..-1]]
  end

  def with_color(color_code, &block)
    print color_code if STDOUT.tty?
    begin
      result = yield block
    ensure
      print TERM_COLOR_DEFAULT if STDOUT.tty?
    end
    result
  end

  def boxed(&block)
    puts ''
    puts '============================================================'
    puts ''
    result = yield block
    puts ''
    puts '============================================================'
    puts ''
    result
  end

  # https://misc.flogisoft.com/bash/tip_colors_and_formatting
  TERM_COLOR_RED = "\e[31m"
  TERM_COLOR_DEFAULT = "\e[39m"
end

module Commands
  include Utilities

  def help
    puts <<~TXT
      Usage: jt [options] COMMAND [command-options]
          Where options are:
          --build                   Runs `jt build` before the command
          --rebuild                 Runs `jt rebuild` before the command
          --use|-u [RUBY_SELECTOR]  Specifies which Ruby interpreter should be used in a given command. jt will apply
                                    options based on the given Ruby interpreter. Allowed values are:
                                    * name given to the --name option during build
                                    * absolute path to a Ruby interpreter executable
                                    * prefix of a Ruby interpreter
                                    * 'ruby' which uses the current Ruby executable in the PATH
                                    Default value is --use jvm, therefore all commands run on truffleruby-jvm by default.
                                    The default can be changed with `export RUBY_BIN=RUBY_SELECTOR`
          --silent|-q               Does not print the command and which Ruby is used
          --verbose|-v              Print more details and commands
          --jdk                     Specifies which version of the JDK should be used: #{JDK_VERSIONS.join(' or ')} (default: #{DEFAULT_JDK_VERSION})

      jt build [graalvm|options|core-symbols] ...   by default it builds graalvm
        jt build options|core-symbols
            options                           build the options
            core-symbols                      build the core symbols
        jt build [options] [mx options] [-- mx build options]
                                              build a GraalVM based on the given env file, the default is a minimal
                                              GraalVM with JVM and Truffleruby available in mxbuild/truffleruby-jvm,
                                              the Ruby is symlinked into rbenv or chruby if available
            options:
              --sforceimports                 run `mx sforceimports` before building (default: false)
              --env|-e                        mx env file used to build the GraalVM, default is "jvm"
              --name|-n NAME                  specify the name of the build "mxbuild/truffleruby-NAME",
                                              it is also linked in your ruby manager (if found) under the same name,
                                              by default it is the name of the mx env file,
                                              the named build stays until it is rebuilt or deleted manually
              --new-hash                      update the git commit hash in RUBY_DESCRIPTION
            mx options:                       options passed directly to mx
              -d                              start the Java debugger and enables assertions when running truffleruby to configure C extensions
            mx build options                  options passed to the 'build' command of mx

      jt build_stats [--json] <attribute>            prints attribute's value from build process (e.g., binary size)
      jt clean                                       clean
      jt env                                         prints the current environment
      jt rebuild [build options]                     clean and build
      jt ruby [jt options] [--] [ruby options] args...
                                                     run TruffleRuby with args
          --stress        stress the compiler (compile immediately, foreground compilation, compilation exceptions are fatal)
          --ea            enable assertions
          --asm           show assembly
          --igv           dump select Graal graphs to graal_dumps/ (-D#{GRAAL_OPTION_PREFIX}.Dump=Truffle:1)
          --igv-full      dump all Graal graphs to graal_dumps/ (-D#{GRAAL_OPTION_PREFIX}.Dump=Truffle:2,TruffleHostInlining:0)
          --igv-network   dump to IGV directly through the network (-D#{GRAAL_OPTION_PREFIX}.PrintGraph=Network)
          --infopoints    show source location for each node in IGV
          --fg            disable background compilation
          --trace         show compilation information on stdout
          --jdebug        start the Java debugger server on port 8000
          --jexception[s] print java exceptions
          --exec          use exec rather than system
      jt gem                                         shortcut for `jt ruby -S gem`, to install Ruby gems, etc
      jt e 14 + 2                                    evaluate an expression
      jt puts 14 + 2                                 evaluate and print an expression
      ---
      ruby/spec commands:
      jt test specs [fast] [mspec arguments] [-- ruby options]
      jt test specs                                  run all specs
      jt test fast                                   run all specs except sub-processes, GC, sleep, ...
      jt test spec/ruby/language                     run specs in this directory
      jt test spec/ruby/language/while_spec.rb       run specs in this file
      jt test ... -- --jdebug                        run specs with the Java debugger
      jt tag PATH                                    tag failing specs
      jt tag all FILE                                tag all specs in this file, without running them
      jt untag ...                                   untag passing specs
      jt purge ...                                   remove tags without specs
      jt mspec ...                                   run MSpec with the TruffleRuby configuration and custom arguments
      ---
      MRI tests commands:
      jt test mri                                    run MRI tests
      #{MRI_TEST_MODULES.map { |k, h| format ' ' * 4 + '%-16s%s', k, h[:help] }.join("\n")}
      jt test mri [--fast] test/mri/tests/test_find.rb [-- <MRI runner options>]
                                                     run tests in given file, -n option of the runner can be used to further
                                                     limit executed test methods
          --fast                                     skip MRI tests using subprocesses
      jt retag [--together] [FILES]                  Remove MRI excludes and re-add as necessary for MRI tests. If FILES are omitted - all the test files are processed
      ---
      jt test basictest                              run MRI's basictest suite
      jt test bootstraptest                          run MRI's bootstraptest suite
      jt test compiler                               run compiler tests
      jt test integration [TESTS]                    run integration tests
      jt test bundle [--jdebug]                      tests using bundler
      jt test gems [TESTS]                           tests using gems
      jt test ecosystem [TESTS] [options]            tests using the wider ecosystem such as bundler, Rails, etc
      jt test cexts [--no-openssl] [--no-gems] [test_names...]
                                                     run C extension tests (set GEM_HOME)
      jt test unit [unittest flags] [-- mx options]  run Java unittests
      jt test tck                                    run tck tests
      ---
      jt lint                                        run all lints, used in CI
      jt lint fast                                   run fast lints, recommended as a git hook (see tool/hooks/lint-check.sh)
      jt rubocop [rubocop options]                   run rubocop rules (using ruby available in the environment)
      ---
      jt metrics alloc [--json] ...                  how much memory is allocated running a program
      jt metrics instructions ...                    how many CPU instructions are used to run a program
      jt metrics minheap ...                         what is the smallest heap you can use to run an application
      jt metrics time ...                            how long does it take to run a command, broken down into different phases
      jt benchmark args... [-- ruby options]         run benchmark-interface
                                       note that to run most MRI benchmarks, you should translate them first with normal
                                       Ruby and cache the result, such as benchmark bench/mri/bm_vm1_not.rb --cache
                                       jt benchmark bench/mri/bm_vm1_not.rb --use-cache
      jt profile                                     profiles an application, including the TruffleRuby runtime, and generates a flamegraph
      jt graph [ruby options] [graph options] [--method Object#foo] file.rb [-- seafoam options]
                                                     render a graph of Object#foo within file.rb
                              --watch                repeat whenever the file changes
                              --simplify             simplify the graph by disabling some optimizations which tend to duplicate parts of the graph
                              --igv                  send the graphs to IGV over the network instead of using seafoam
                              --describe             describe the shape of the graph (linear, branches, loops, calls, deopts)
                              --cfg2asm              use cfg2asm to print assembly code
                                --no-comments        do not print comments in the assembly code
                              SEAFOAM_DIR            use the seafoam in the given directory
                              CFG2ASM_DIR            use the cfg2asm in the given directory
      jt igv                                         launches IdealGraphVisualizer
      jt next                                        tell you what to work on next (give you a random core library spec)
      jt install [jvmci|eclipse]                     install [the right JVMCI JDK | Eclipse] in the parent directory
      jt docker                                      build a Docker image - see doc/contributor/docker.md
      jt sync                                        continuously synchronize changes from the Ruby source files to the GraalVM build
      jt idea                                        generates IntelliJ projects
      jt format                                      run eclipse code formatter
      jt ruby-home                                   prints the Ruby home of the RUBY_SELECTOR

      you can also put --build or --rebuild in front of any command to build or rebuild first

      recognised environment variables:

        RUBY_BIN                                     The TruffleRuby executable to use (normally just bin/truffleruby)
        JAVA_HOME                                    Path to the JVMCI JDK used for building with mx
        ECLIPSE_EXE                                  Where to find Eclipse
        SYSTEM_RUBY                                  The Ruby interpreter to run 'jt' itself, when using 'bin/jt'
        JT_JDK                                       The default JDK version to use: #{JDK_VERSIONS.join(' or ')}
        JT_ENV                                       The default value for 'jt build --env JT_ENV' and for 'jt --use JT_ENV'
        JT_PROFILE_SUBCOMMANDS                       Print the time each subprocess takes on stderr
        JT_SPECS_COMPILATION                         Controls whether Graal will be used when running specs (default: 'true'). Only affects JVM with Graal. Set to 'false' to disable for running specs faster when developing with jvm-ce.
    TXT
  end

  def truffle_version
    puts get_truffle_version
  end

  def truffleruby_version
    puts get_truffleruby_version
  end

  def jvm_standalone_release_archive
    puts jvm_standalone_release_archive_path
  end

  def native_standalone_release_archive
    puts native_standalone_release_archive_path
  end

  def mx(*args)
    super(*args)
  end
  ruby2_keywords :mx if respond_to?(:ruby2_keywords, true)

  def launcher
    puts ruby_launcher
  end

  # Not `def ruby_home` because calls to `ruby_home` would then unintentionally print
  define_method(:'ruby-home') do
    @silent = true
    puts ruby_home
  end

  def build(*options)
    in_truffleruby_repo_root!
    project = options.shift
    case project
    when 'options'
      sh 'tool/generate-options.rb'
    when 'core-symbols'
      sh 'tool/generate-core-symbols.rb'
    when 'cexts'
      mx 'build', '--dependencies', 'org.truffleruby.cext'
    else
      build_standalone(*project, *options)
    end
  end

  def clean(*options)
    in_truffleruby_repo_root!
    project = options.shift
    case project
    when 'cexts'
      mx 'clean', '--dependencies', 'org.truffleruby.cext'
    when nil
      mx 'clean'
      sh 'rm', '-rf', 'spec/ruby/ext'
      Dir.glob("#{TRUFFLERUBY_DIR}/mxbuild/{*,.*}") do |path|
        next if File.basename(path).start_with?('truffleruby-')
        next if %w(. ..).include? File.basename(path)
        sh 'rm', '-rf', path
      end
    else
      raise ArgumentError, project
    end
  end

  def env
    puts 'Environment'
    env_vars = %w[JAVA_HOME PATH RUBY_BIN TRUFFLERUBYOPT RUBYOPT]
    column_size = env_vars.map(&:size).max
    env_vars.each do |e|
      puts format "%#{column_size}s: %s", e, ENV[e].inspect
    end
    shell = -> command { sh(*command.split, continue_on_failure: true) }
    shell['ruby -v']
    shell['uname -a']
    shell['cc -v']
    shell['gcc -v']
    shell['clang -v']
    shell['opt -version']
    shell['brew --prefix llvm@4']
    begin
      llvm = `brew --prefix llvm@4`.chomp
      shell["#{llvm}/bin/clang -v"]
      shell["#{llvm}/bin/opt -version"]
    rescue Errno::ENOENT
      # No Homebrew
    end
    shell['mx version']
    sh('mx', 'sversions', continue_on_failure: true)
    shell['git --no-pager show -s --format=%H']
    shell['openssl version']
    shell['java -version']
  end

  def rebuild(*options)
    case options
    when ['cexts']
      clean(*options)
      build
    else
      clean
      build(*options)
    end
  end

  private def ruby_options(options, args)
    raise ArgumentError, args.inspect + ' has non-String values' if args.any? { |v| not v.is_a? String }

    ruby_args = []
    vm_args = []

    core_load_path = true
    experimental_options_added = false

    add_experimental_options = -> do
      unless experimental_options_added
        experimental_options_added = true
        vm_args << '--experimental-options'
      end
    end

    while (arg = args.shift)
      case arg
      when '--jvm'
        @truffleruby_jvm_arg ||= true
        vm_args << arg
      when '--no-core-load-path'
        core_load_path = false
      when '--ea'
        vm_args += %w[--vm.ea --vm.esa] if truffleruby_jvm?
      when '--check-compilation'
        add_experimental_options.call
        vm_args << '--engine.CompilationFailureAction=ExitVM'
        vm_args << '--compiler.TreatPerformanceWarningsAsErrors=all'
      when '--stress'
        add_experimental_options.call
        vm_args << '--engine.CompileImmediately'
        vm_args << '--engine.BackgroundCompilation=false'
        args.unshift '--check-compilation'
      when '--asm'
        vm_args += %w[--vm.XX:+UnlockDiagnosticVMOptions --vm.XX:CompileCommand=print,*::callRoot]
      when '--jacoco'
        vm_args << jacoco_args
      when '--jdebug'
        vm_args << JDEBUG
      when '--jexception', '--jexceptions'
        add_experimental_options.call
        vm_args << '--exceptions-print-uncaught-java=true'
      when '--infopoints'
        if truffleruby_jvm?
          vm_args << '--vm.XX:+UnlockDiagnosticVMOptions' << '--vm.XX:+DebugNonSafepoints'
        end
        vm_args << '--compiler.NodeSourcePositions=true'
      when '--fg'
        add_experimental_options.call
        vm_args << '--engine.BackgroundCompilation=false'
      when '--trace'
        truffleruby_compiler!
        vm_args << '--engine.TraceCompilation'
      when '--igv', '--igv-full'
        truffleruby_compiler!
        if arg == '--igv-full'
          vm_args << "--vm.D#{GRAAL_OPTION_PREFIX}.Dump=Truffle:2,TruffleHostInlining:0,TruffleInjectImmutableFrameFields:0"
        else
          vm_args << "--vm.D#{GRAAL_OPTION_PREFIX}.Dump=Truffle:1"
        end
        vm_args << "--vm.D#{GRAAL_OPTION_PREFIX}.PrintBackendCFG=false"
      when '--igv-network'
        truffleruby_compiler!
        vm_args << "--vm.D#{GRAAL_OPTION_PREFIX}.PrintGraph=Network"
      when '--exec'
        options[:use_exec] = true
      when /^--vm\./
        vm_args << arg
      when '--'
        # marks rest of the options as Ruby arguments, stop parsing jt options
        break
      else
        ruby_args.push arg
        # do not continue looking for jt options when we encounter an argument
        break unless arg.start_with? '-'
      end
    end

    if core_load_path and truffleruby_jvm? and used_ruby_is_mx_env_name?
      add_experimental_options.call
      vm_args << "--core-load-path=#{TRUFFLERUBY_DIR}/src/main/ruby/truffleruby"
    end

    [vm_args, ruby_args + args, options]
  end

  private def run_ruby(*args)
    env_vars = args.first.is_a?(Hash) ? args.shift : {}
    options = args.last.is_a?(Hash) ? args.pop : {}

    vm_args, ruby_args, options = ruby_options(options, args)

    sh env_vars, ruby_launcher, *(vm_args if truffleruby?), *ruby_args, options
  end

  def ruby(*args)
    require_ruby_launcher!
    env = args.first.is_a?(Hash) ? args.shift : {}
    run_ruby(env, '--exec', *args)
  end

  # Legacy alias
  alias_method :run, :ruby

  def e(*args)
    ruby '-e', args.join(' ')
  end

  def command_puts(*args)
    e 'puts begin', *args, 'end'
  end

  def command_p(*args)
    e 'p begin', *args, 'end'
  end

  # Just convenience
  def gem(*args)
    ruby '-S', 'gem', *args
  end

  def cextc(cext_dir)
    require_ruby_launcher!
    cext_dir = File.expand_path(cext_dir)
    name = File.basename(cext_dir)
    ext_dir = "#{cext_dir}/ext/#{name}"
    target = "#{cext_dir}/lib/#{name}/#{name}.#{DLEXT}"
    compile_cext(name, ext_dir, target)
  end

  private def compile_cext(name, ext_dir, target, env: {})
    extconf = "#{ext_dir}/extconf.rb"
    raise "#{extconf} does not exist" unless File.exist?(extconf)

    chdir(ext_dir) do
      run_ruby(env, '-rmkmf', "#{ext_dir}/extconf.rb") # -rmkmf is required for C ext tests
      if File.exist?('Makefile')
        sh('make')
        FileUtils::Verbose.cp("#{name}.#{DLEXT}", target) if target
      else
        STDERR.puts "Makefile not found in #{ext_dir}, skipping make."
      end
    end
  end

  def test(*args)
    require_ruby_launcher!
    path, *rest = args

    case path
    when nil
      %w[specs mri bundle cexts integration gems ecosystem compiler].each do |kind|
        jt('test', kind)
      end
    when 'bundle' then test_bundle(*rest)
    when 'compiler' then test_compiler(*rest)
    when 'cexts' then test_cexts(*rest)
    when 'integration' then test_integration(*rest)
    when 'gems' then test_gems(*rest)
    when 'ecosystem' then test_ecosystem(*rest)
    when 'polyglot' then test_polyglot(*rest)
    when 'specs' then test_specs('run', *rest)
    when 'basictest' then test_basictest(*rest)
    when 'bootstraptest' then test_bootstraptest(*rest)
    when 'mri' then test_mri(*rest)
    when 'unit', 'unittest' then test_unit(*rest)
    when 'tck'
      puts bold 'NOTE: You need `jt mx build` before running `jt test tck` to build the relevant test distributions'
      # GR-48568 for why DisableClassPathIsolation is needed
      mx 'tck', '-Dpolyglotimpl.DisableClassPathIsolation=true', *rest
    else
      if File.expand_path(path, TRUFFLERUBY_DIR).start_with?("#{TRUFFLERUBY_DIR}/test")
        test_mri(*args)
      else
        test_specs('run', *args)
      end
    end
  end

  def test_unit(*rest)
    mx('build', '--dependencies', 'TRUFFLERUBY-TEST-EMBEDDING,TRUFFLERUBY-TEST-INTERNAL')
    unittest_args, mx_options = args_split(rest)
    unittest_options, tests = unittest_args.partition { |arg| arg.start_with?('-') }

    if tests.empty?
      mx(*mx_options, 'unittest', *unittest_options, 'org.truffleruby.test.embedding')
      mx(*mx_options, 'unittest', *unittest_options, '--force-classpath', 'org.truffleruby.test.internal')
    else
      if tests.any? { |test| test.include?('internal') }
        unittest_options << '--force-classpath'
      end
      mx(*mx_options, 'unittest', *unittest_options, *tests)
    end
  end

  private def jt(*args)
    measure(args.join(' ')) do
      sh RbConfig.ruby, 'tool/jt.rb', *args
    end
  end

  private def test_basictest(*args)
    run_runner_test 'basictest/runner.rb', *args
  end

  private def test_bootstraptest(*args)
    run_runner_test 'bootstraptest/runner.rb', *args
  end

  private def run_runner_test(runner, *args)
    double_dash_index = args.index '--'
    if double_dash_index
      args, runner_args = args[0...double_dash_index], args[(double_dash_index+1)..-1]
    else
      runner_args = []
    end
    run_ruby(*args, "#{TRUFFLERUBY_DIR}/test/#{runner}", *runner_args)
  end

  private def test_mri(*args)
    double_dash_index = args.index '--'
    if double_dash_index
      args, runner_args = args[0...double_dash_index], args[(double_dash_index+1)..-1]
    else
      runner_args = []
    end

    mri_args = []
    excluded_files = load_excluded_file_names
    patterns = []

    args.each do |arg|
      test_module = MRI_TEST_MODULES[arg]
      if test_module
        patterns.push(*test_module[:include])
        excluded_files.concat test_module[:exclude].to_a.flat_map { |pattern|
          Dir.glob("#{MRI_TEST_PREFIX}/#{pattern}").sort.map { |path| mri_test_name(path) }
        }
      elsif arg.start_with?('-')
        mri_args.push arg
      else
        patterns.push arg
      end
    end

    files_to_run = get_mri_test_files(patterns)
    files_to_run -= excluded_files

    run_mri_tests(mri_args, files_to_run, runner_args, use_exec: true)
  end

  private def get_mri_test_files(patterns)
    patterns << "#{MRI_TEST_PREFIX}/**/{test_*,*_test}.rb" if patterns.empty?

    patterns.flat_map do |pattern|
      if pattern.start_with?(MRI_TEST_RELATIVE_PREFIX)
        pattern = "#{TRUFFLERUBY_DIR}/#{pattern}"
      elsif !pattern.start_with?(MRI_TEST_PREFIX)
        pattern = "#{MRI_TEST_PREFIX}/#{pattern}"
      end

      glob = Dir.glob(pattern).flat_map do |path|
        if File.directory?(path)
          Dir.glob("#{path}/**/{test_*,*_test}.rb")
        else
          path
        end
      end
      abort "pattern #{pattern} matched no files" if glob.empty?
      glob.map { |path| mri_test_name(path) }
    end.sort
  end

  private def mri_test_name(test)
    prefix = "#{MRI_TEST_RELATIVE_PREFIX}/"
    abs_prefix = "#{MRI_TEST_PREFIX}/"
    if test.start_with?(prefix)
      test[prefix.size..-1]
    elsif test.start_with?(abs_prefix)
      test[abs_prefix.size..-1]
    else
      test
    end
  end

  private def run_mri_tests(extra_args, test_files, runner_args, run_options)
    abort 'No test files! (probably filtered out by failing.exclude)' if test_files.empty?
    test_files = test_files.map { |file| mri_test_name(file) }

    truffle_args = []
    if truffleruby?
      vm_args, ruby_args, _parsed_options = ruby_options({}, %w[--ea --experimental-options --testing-rubygems])
      truffle_args = vm_args + ruby_args
    end

    env_vars = {
      'EXCLUDES' => 'test/mri/excludes',
      'RUBYGEMS_TEST_PATH' => "#{MRI_TEST_PREFIX}/rubygems",
      'RUBYOPT' => [*ENV['RUBYOPT'], '--disable-gems'].join(' '),
      'TRUFFLERUBYOPT' => [*ENV['TRUFFLERUBYOPT'], *truffle_args].join(' '),
    }

    if extra_args.delete '--fast'
      env_vars['MRI_TEST_SUBPROCESSES'] = 'false'
    end

    cext_tests = test_files.select do |f|
      f.include?('cext-ruby') || MRI_TEST_CAPI_TESTS.include?(f)
    end

    compile_cext_for_mri_c_api_tests(cext_tests)

    command = %w[test/mri/tests/runner.rb -v --test-order=sorted --color=never --tty=no -q]
    command.unshift("-I#{TRUFFLERUBY_DIR}/.ext")  if !cext_tests.empty?
    run_ruby(env_vars, *extra_args, *command, *test_files, *runner_args, run_options)
  end

  private def compile_cext_for_mri_c_api_tests(cext_tests)
    compile_env = {
      # MRI C-ext tests expect to be built with $extmk = true.
      'MKMF_SET_EXTMK_TO_TRUE' => 'true',
    }

    cext_tests.each do |test|
      puts
      puts test
      test_path = "#{MRI_TEST_PREFIX}/#{test}"
      match = File.read(test_path).match(/\brequire ['"]c\/(.*?)["']/)
      if match
        cext_name = match[1]
        compile_dir = if Dir.exist?("#{MRI_TEST_CEXT_DIR}/#{cext_name}")
                        "#{MRI_TEST_CEXT_DIR}/#{cext_name}"
                      elsif cext_name.include?('/')
                        "#{MRI_TEST_CEXT_DIR}/#{File.dirname(cext_name)}"
                      else
                        "#{MRI_TEST_CEXT_DIR}/#{cext_name.gsub('_', '-')}"
                      end
        # Remove depend files copied from MRI as they hardcode header locations
        FileUtils::Verbose.rm_f("#{compile_dir}/depend")

        name = File.basename(match[1])
        target_dir = if match[1].include?('/')
                       File.dirname(match[1])
                     else
                       ''
                     end
        dest_dir = File.join(MRI_TEST_CEXT_LIB_DIR, target_dir)
        FileUtils::Verbose.mkdir_p(dest_dir)
        compile_cext(name, compile_dir, dest_dir, env: compile_env)
      else
        puts "c require not found for cext test: #{test_path}"
      end
    end
  end

  def retag(*args)
    in_truffleruby_repo_root!
    require_ruby_launcher!
    options, test_files = args.partition { |a| a.start_with?('-') }
    together = options.delete('--together')

    test_files = get_mri_test_files(test_files)

    excluded_files = load_excluded_file_names
    files_to_skip = test_files & excluded_files
    files_to_retag = test_files - excluded_files

    puts 'The following files are excluded in test/mri/failing.exclude:'
    puts files_to_skip.map { |s| '- ' + s }

    puts 'The following files will be retagged:'
    puts files_to_retag.map { |s| '- ' + s }

    files_to_retag_relative = files_to_retag # paths are relative to test/mri/tests directory
    files_to_retag = files_to_retag_relative.map { |test_file| "#{MRI_TEST_RELATIVE_PREFIX}/#{test_file}" }

    # Detect test classes in runtime
    #
    # It's difficult to find out test classes declared in a test file properly by static analysis (probably only parsing and checking AST may work)
    # There are the following issues with static analysis:
    #   - there may be a base class for several test classes - so we cannot just look up with a regexp for classes that directly inherit Test::Unit::TestCase
    #   - test classes can inherit one another
    #   - test classes may share common test cases by including a module with test cases
    #   - there may be several test classes in a single test file

    # build native extensions needed for MRI tests
    cext_tests = files_to_retag_relative.select do |f|
      f.include?('cext-ruby') || MRI_TEST_CAPI_TESTS.include?(f)
    end
    compile_cext_for_mri_c_api_tests(cext_tests)

    command = ['tool/retag_helper.rb']
    command.unshift("-I#{TRUFFLERUBY_DIR}/.ext") if !cext_tests.empty?
    output = run_ruby(*command, *files_to_retag, capture: :both)
    test_classes = output.lines.map(&:chomp)
    if test_classes.empty?
      puts "\nWARNING: Could not find class inheriting from TestCase in #{files_to_retag}"
      return
    end

    puts "Preprocess exclude files for classes: #{test_classes}"
    test_classes.each do |test_class|
      prefix = "test/mri/excludes/#{test_class.gsub('::', '/')}"
      ["#{prefix}.rb", prefix].each do |file|
        if File.exist?(file) && File.file?(file)
          lines = File.readlines(file)
          FileUtils::Verbose.rm_r file

          # We know some tests hang and odds are very good they're going to continue to hang, so let's keep those
          # tests as excluded and manually inspect them later. We need to be careful that we're not checking for our
          # retains pattern on the test name. Thus, we limit our checks to either the exclusion reason string or a
          # conditional guard that applies to the exclusion.
          retain = lines.select do |line|
            reason_or_guard = line.split(',', 2).last

            MRI_TEST_RETAG_RETAIN_PATTERNS.any? { |pattern| reason_or_guard =~ pattern }
          end

          unless retain.empty?
            puts 'Retaining:'
            puts retain
            File.write(file, retain.sort.join)
          end
        end
      end
    end

    if together
      groups = [files_to_retag]
    else
      groups = files_to_retag.map { |file| [file] }
    end

    groups.each do |group|
      process_tests = true
      while process_tests
        puts '1. Tagging tests'
        output_file = 'mri_tests.txt'
        run_mri_tests(options, group, [], [:err, :out] => output_file, continue_on_failure: true)

        # Uncomment this to debug retagging and test class/method name capturing
        # puts ">"*80
        # puts "*** mri_tests.txt:"
        # puts ">"*80
        # puts File.read(output_file)
        # puts "<"*80

        puts '2. Parsing errors'
        sh 'ruby', 'tool/parse_mri_errors.rb', output_file, continue_on_failure: true

        process_tests = $LAST_SH_STATUS.exitstatus == 2
      end

      puts '3. Verifying tests pass'
      run_mri_tests(options, group, [], use_exec: groups.size == 1)
    end
  end

  private def test_compiler(*args)
    truffleruby_compiler!

    run_tests('test/truffle/compiler', args) do |test_script|
      sh test_script
    end
  end

  private def jacoco_args
    out = mx('ruby_jacoco_args', '--quiet', '--no-warning', capture: :out).lines.last.chomp
    out.sub('-', '--vm.')
  end

  GEMS_CEXTS_TESTS = %w[
    oily_png psd_native
    puma sqlite3 unf_ext json grpc RubyInline msgpack
  ]

  ALL_CEXTS_TESTS = %w[
    tools postinstallhook
    minimum module method globals backtraces xopenssl werror no_timespec stripped
  ] + GEMS_CEXTS_TESTS

  private def test_cexts(*args)
    no_openssl = args.delete('--no-openssl')
    no_gems = args.delete('--no-gems')
    tests = args.empty? ? ALL_CEXTS_TESTS : args
    tests -= %w[xopenssl] if no_openssl
    tests -= GEMS_CEXTS_TESTS if no_gems

    tests.each do |test_name|
      run_single_cexts_test(test_name)
    end
  end

  private def run_single_cexts_test(test_name)
    in_truffleruby_repo_root!
    raise "#{test_name} not in ALL_CEXTS_TESTS" unless ALL_CEXTS_TESTS.include?(test_name)

    measure("jt test cexts #{test_name}") do
      case test_name
      when 'tools'
        # Test tools
        run_ruby 'test/truffle/cexts/test_preprocess.rb'

      when 'minimum', 'module', 'method', 'globals', 'backtraces', 'xopenssl', 'werror', 'no_timespec', 'stripped'
        # Test that we can compile and run some very basic C extensions
        output_file = 'cext-output.txt'
        dir = "test/truffle/cexts/#{test_name}"
        cextc(dir)
        script = "#{dir}/bin/#{test_name}"
        # bin/backtraces relies on being run with an absolute path for __FILE__
        script = "#{TRUFFLERUBY_DIR}/#{script}" if test_name == 'backtraces'
        run_ruby "-I#{dir}/lib", script, out: output_file, continue_on_failure: true
        begin
          actual = File.read(output_file)
          expected_file = "#{dir}/expected.txt"
          expected = File.read(expected_file)
          unless actual == expected
            abort <<~EOS
              C extension #{dir} didn't work as expected

              Actual:
              #{actual}

              Expected:
              #{expected}

              Diff:
              #{diff(expected_file, output_file)}
            EOS
          end
        ensure
          File.delete output_file if File.exist? output_file
        end

      when 'postinstallhook'
        # Test that running the post-install hook works, even when opt &
        # llvm-link are not on PATH, as it is the case on macOS.
        sh({'TRUFFLERUBY_RECOMPILE_OPENSSL' => 'true'}, "#{ruby_home}/lib/truffle/post_install_hook.sh")

      when 'oily_png', 'psd_native'
        dir = "test/truffle/cexts/#{test_name}"
        run_ruby('-Sbundle', 'install', chdir: dir)
        run_ruby('-rbundler/setup', 'test.rb', chdir: dir)

      # Tests using gem install to compile the cexts
      when 'puma'
        sh 'test/truffle/cexts/puma/puma.sh'
      when 'sqlite3'
        sh 'test/truffle/cexts/sqlite3/sqlite3.sh'
      when 'unf_ext'
        sh 'test/truffle/cexts/unf_ext/unf_ext.sh'
      when 'json'
        sh 'test/truffle/cexts/json/json.sh'
      when 'grpc'
        sh 'test/truffle/cexts/grpc/grpc.sh'

      when 'RubyInline'
        # Test a gem dynamically compiling a C extension
        # Does not work on macOS. Also fails on macOS on MRI with --enabled-shared.
        # It's a bug of RubyInline not using LIBRUBYARG/LIBRUBYARG_SHARED.
        sh 'test/truffle/cexts/RubyInline/RubyInline.sh' unless darwin?

      # Test cexts used by many projects
      when 'msgpack'
        sh 'test/truffle/cexts/msgpack/msgpack.sh'
      else
        raise "unknown test: #{test_name}"
      end
    end
  end

  private def run_tests(tests_path, tests)
    tests_path = "#{TRUFFLERUBY_DIR}/#{tests_path}"
    test_names = tests.empty? ? '*' : '{' + tests.join(',') + '}'

    candidates = Dir["#{tests_path}/#{test_names}.sh"].sort
    if candidates.empty?
      targets = Dir["#{tests_path}/*.sh"].sort.map { |f| File.basename(f, '.sh') }
      puts "No targets found by pattern #{test_names}. Available targets: "
      targets.each { |t| puts " * #{t}" }
      exit 1
    end

    STDERR.puts
    candidates.each do |test_script|
      measure("jt test #{File.basename(tests_path)} #{File.basename(test_script, '.sh')}") do
        yield test_script
      end
    end
  end

  private def measure(command)
    STDERR.puts "[jt] Running #{command} ..."
    start = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    begin
      yield
    ensure
      finish = Process.clock_gettime(Process::CLOCK_MONOTONIC)
      duration = finish - start
      STDERR.puts "[jt] #{command} took #{'%.1f' % duration}s\n\n\n"
    end
  end

  private def test_integration(*args)
    run_tests('test/truffle/integration', args) do |test_script|
      sh test_script
    end
  end

  private def test_gems(*args)
    run_tests('test/truffle/gems', args) do |test_script|
      sh test_script
    end
  end

  private def test_ecosystem(*args)
    run_tests('test/truffle/ecosystem', args) do |test_script|
      sh test_script
    end
  end

  private def test_polyglot(*args)
    run_tests('test/truffle/polyglot', args) do |test_script|
      sh test_script
    end
  end

  def find_ports_for_pid(pid)
    while (ports = `lsof -P -i 4 -a -p #{pid} -Fn | grep nlocalhost | cut -d ':' -f 2`.strip).empty?
      sleep 1
    end
    puts ports
    ports
  end

  private def test_bundle(*args)
    require 'tmpdir'

    bundle_install_flags = [
      %w[],
      %w[--standalone],
      %w[--deployment]
    ]

    bundle_install_flags.each do |install_flags|
      puts "\n\nTesting Bundler with install flags: #{install_flags}"
      temp_dir = Dir.mktmpdir('algebrick')
      begin
        gem_home = "#{temp_dir}/gems"
        puts "Using temporary GEM_HOME: #{gem_home}"

        gem_source_tree = "#{temp_dir}/algebrick"
        sh 'git', 'clone', '--branch', 'v0.7.5', 'https://github.com/pitr-ch/algebrick.git', gem_source_tree

        overlays = "#{TRUFFLERUBY_DIR}/test/truffle/bundle/algebrick"
        FileUtils.copy_entry(overlays, gem_source_tree)

        chdir(gem_source_tree) do
          environment = no_gem_vars_env.merge(
            'GEM_HOME' => gem_home,
            'GEM_PATH' => gem_home,
            # add bin from gem_home to PATH
            'PATH' => ["#{gem_home}/bin", ENV['PATH']].join(File::PATH_SEPARATOR))

          options = %w[--experimental-options --exceptions-print-java]

          run_ruby(environment, *args, *options,
            '-Sbundle', 'install', '-V', *install_flags)

          run_ruby(environment, *args, *options,
            '-Sbundle', 'exec', '-V', 'rake')
        end
      ensure
        STDERR.puts 'Removing temp dir'
        FileUtils.remove_entry_secure temp_dir
      end
    end
  end

  def mspec(*args)
    require_ruby_launcher!
    run_mspec({}, *args)
  end

  private def test_specs(command, *args)
    env_vars = {}
    options = []
    tag = ENV['TAG'] || 'fails'

    case command
    when 'run'
      options += %w[--excl-tag fails]
    when 'tag'
      options += %W[--add #{tag} --fail --excl-tag fails]
    when 'untag'
      options += %W[--del #{tag} --pass]
      command = 'tag'
    when 'purge'
      options += %w[--purge]
      command = 'tag'
    when 'tag_all'
      options += %W[--unguarded --all --dry-run --add #{tag}]
      command = 'tag'
    else
      raise command
    end

    if args.first == 'fast'
      args.shift
      options += %w[--excl-tag slow]
    end

    options += %w[--timeout 180] if ENV['CI']

    args, ruby_args = args_split(args)

    # always enable assertions (with jt's --ea flag) to catch issues earlier
    vm_args, ruby_args, parsed_options = ruby_options({}, ['--ea', *ruby_args])

    if !JT_SPECS_COMPILATION && truffleruby_compiler? && truffleruby_jvm?
      # Note: GR-62802: --vm.XX:-UseJVMCICompiler does not work for unchained standalones or needs --vm.Dpolyglotimpl.DisableVersionChecks=true
      vm_args << '--vm.XX:-UseJVMCICompiler'
      vm_args << '--experimental-options' << '--engine.Compilation=false'
      vm_args << '--engine.Splitting=false' unless JT_SPECS_SPLITTING
    end

    raise "unsupported options #{parsed_options}" unless parsed_options.empty?

    prefixed_ruby_args = [*(vm_args if truffleruby?), *ruby_args].map { |v| "-T#{v}" }
    run_mspec env_vars, command, *options, *prefixed_ruby_args, *args
  end

  def tag(path, *args)
    require_ruby_launcher!
    return tag_all(*args) if path == 'all'
    test_specs('tag', path, *args)
  end

  # Add tags to all given examples without running them. Useful to avoid file exclusions.
  private def tag_all(*args)
    test_specs('tag_all', *args)
  end

  def purge(path, *args)
    require_ruby_launcher!
    test_specs('purge', path, *args)
  end

  def untag(path, *args)
    require_ruby_launcher!
    test_specs('untag', path, *args)
  end

  def build_stats(attribute, *args)
    require_ruby_launcher!

    use_json = args.delete '--json'

    value = case attribute
            when 'binary-size'
              build_stats_native_binary_size
            when 'build-time'
              build_stats_native_build_time
            when 'runtime-compilable-methods'
              build_stats_native_runtime_compilable_methods
            else
              raise ArgumentError, attribute
            end

    if use_json
      puts JSON.generate({ attribute => value })
    else
      puts "#{attribute}: #{value}"
    end
  end

  private def build_stats_native_binary_size
    truffleruby_native!
    File.size(language_lib_path) / 1024.0 / 1024.0
  end

  private def build_stats_native_build_time
    require 'json'
    data = JSON.load(File.read('native-image-build-rubyvm.json'))
    data.fetch('resource_usage').fetch('total_secs')
  end

  private def build_stats_native_runtime_compilable_methods
    require 'json'
    data = JSON.load(File.read('native-image-build-rubyvm.json'))
    data.fetch('image_details').fetch('runtime_compiled_methods').fetch('count')
  end

  def metrics(command, *args)
    require_ruby_launcher!
    ENV['TRUFFLERUBY_ALLOW_PRIVATE_PRIMITIVES_IN'] = "#{TRUFFLERUBY_DIR}/bench/metrics/"
    args = args.dup
    case command
    when 'alloc'
      metrics_alloc(*args)
    when 'minheap'
      metrics_minheap(*args)
    when 'maxrss'
      metrics_maxrss(*args)
    when 'instructions'
      metrics_native_instructions(*args)
    when 'time'
      metrics_time(*args)
    else
      raise ArgumentError, command
    end
  end

  private def metrics_alloc(*args)
    use_json = args.delete '--json'
    samples = []
    METRICS_REPS.times do
      log '.', "sampling\n"
      out = run_ruby '--vm.Dtruffleruby.metrics.memory_used_on_exit=true', '--vm.verbose:gc', *args, capture: :both, no_print_cmd: true
      samples.push memory_allocated(out)
    end
    log "\n", nil
    range = samples.max - samples.min
    error = range / 2
    median = samples.min + error
    human_readable = "#{human_size(median)} ± #{human_size(error)}"
    if use_json
      puts JSON.generate({
          samples: samples,
          median: median,
          error: error,
          human: human_readable
      })
    else
      puts human_readable
    end
  end

  private def memory_allocated(trace)
    allocated = 0
    trace.lines do |line|
      case line
      when /(\d+)K->(\d+)K/
        before = $1.to_i * 1024
        after = $2.to_i * 1024
        collected = before - after
        allocated += collected
      when /^allocated (\d+)$/
        allocated += $1.to_i
      end
    end
    allocated
  end

  private def metrics_minheap(*args)
    use_json = args.delete '--json'
    heap = 10
    log '>', "Trying #{heap} MB\n"
    until can_run_in_heap(heap, *args)
      heap += 10
      log '>', "Trying #{heap} MB\n"
    end
    heap -= 9
    heap = 1 if heap == 0
    successful = 0
    loop do
      if successful > 0
        log '?', "Verifying #{heap} MB\n"
      else
        log '+', "Trying #{heap} MB\n"
      end
      if can_run_in_heap(heap, *args)
        successful += 1
        break if successful == METRICS_REPS
      else
        heap += 1
        successful = 0
      end
    end
    log "\n", nil
    human_readable = "#{heap} MB"
    if use_json
      puts JSON.generate({
          min: heap,
          human: human_readable
      })
    else
      puts human_readable
    end
  end

  private def can_run_in_heap(heap, *command)
    run_ruby("--vm.Xmx#{heap}M", *command, err: '/dev/null', out: '/dev/null', no_print_cmd: true, continue_on_failure: true, timeout: 60)
  end

  private def metrics_maxrss(*args)
    truffleruby_native!

    use_json = args.delete '--json'
    samples = []

    METRICS_REPS.times do
      log '.', "sampling\n"

      max_rss_in_mb = if linux?
                        out = sh('/usr/bin/time', '-v', '--', ruby_launcher, *args, capture: :both, no_print_cmd: true)
                        out =~ /Maximum resident set size \(kbytes\): (?<max_rss_in_kb>\d+)/m
                        Integer($~[:max_rss_in_kb]) / 1024.0
                      elsif darwin?
                        out = sh('/usr/bin/time', '-l', '--', ruby_launcher, *args, capture: :both, no_print_cmd: true)
                        out =~ /(?<max_rss_in_bytes>\d+)\s+maximum resident set size/m
                        Integer($~[:max_rss_in_bytes]) / 1024.0 / 1024.0
                      else
                        raise "Can't measure RSS on this platform."
                      end

      samples.push(maxrss: max_rss_in_mb)
    end
    log "\n", nil

    results = {}
    samples[0].each_key do |region|
      region_samples = samples.map { |s| s[region] }
      mean = region_samples.inject(:+) / samples.size
      human = "#{region} #{mean.round(2)} MB"
      results[region] = {
          samples: region_samples,
          mean: mean,
          human: human
      }
      if use_json
        file = STDERR
      else
        file = STDOUT
      end
      file.puts region[/\s*/] + human
    end
    if use_json
      puts JSON.generate(Hash[results.map { |key, values| [key, values] }])
    end
  end

  private def metrics_native_instructions(*args)
    truffleruby_native!

    use_json = args.delete '--json'

    out = sh('perf', 'stat', '-e', 'instructions', '--', ruby_launcher, *args, capture: :both, no_print_cmd: true)

    out =~ /(?<instruction_count>[\d,]+)\s+instructions/m
    instruction_count = $~[:instruction_count].gsub(',', '')

    log "\n", nil
    human_readable = "#{instruction_count} instructions"
    if use_json
      puts JSON.generate({
          instructions: Integer(instruction_count),
          human: human_readable
      })
    else
      puts human_readable
    end
  end

  private def metrics_time_measure(use_json, *args)
    truffleruby!
    metrics_time_option = '--vm.Dtruffleruby.metrics.time=true'
    verbose_gc_flag = truffleruby_native? ? '--vm.XX:+PrintGC' : '--vm.verbose:gc' unless use_json
    args = [metrics_time_option, *verbose_gc_flag, '--no-core-load-path', *args]
    # JVM verbose:gc outputs on stdout, metrics outputs on stderr
    capture = truffleruby_native? ? :err : :both

    samples = METRICS_REPS.times.map do
      log '.', "sampling\n"
      start = Time.now
      out = run_ruby(*args, no_print_cmd: true, capture: capture)
      finish = Time.now
      get_times(out, (finish - start) * 1000.0)
    end
    log "\n", nil
    samples
  end

  private def metrics_time(*args)
    use_json = args.delete '--json'
    flamegraph = args.delete '--flamegraph'

    samples = metrics_time_measure(use_json, *args)
    metrics_time_format_results(samples, use_json, flamegraph)
  end

  def format_time_metrics(*args)
    use_json = args.delete '--json'
    flamegraph = args.delete '--flamegraph'

    data = STDIN.read
    times = data.lines.grep(/^(before|after)\b/)
    total = times.last.split.last.to_f - times.first.split.last.to_f
    samples = [get_times(data, total)]

    metrics_time_format_results(samples, use_json, flamegraph)
  end

  private def metrics_time_format_results(samples, use_json, flamegraph)
    min_time = Float(ENV.fetch('TRUFFLERUBY_METRICS_MIN_TIME', '-1'))

    results = {}
    mean_by_stack = {}
    samples[0].each_key do |stack|
      region_samples = samples.map { |s| s[stack] }
      mean = region_samples.inject(:+) / samples.size
      mean_by_stack[stack] = mean
      mean_in_seconds = (mean / 1000.0)

      region = stack.last
      human = "#{'%.3f' % mean_in_seconds} #{region}"
      results[region] = {
        samples: region_samples,
        mean: mean_in_seconds,
        human: human
      }

      indent = ' ' * (stack.size-1)
      if use_json
        STDERR.puts indent + human
      else
        STDOUT.puts indent + human if mean_in_seconds > min_time
      end
    end
    if use_json
      puts JSON.generate(results)
    elsif flamegraph
      repo = find_or_clone_repo('https://github.com/eregon/FlameGraph.git', 'graalvm')
      path = "#{TRUFFLERUBY_DIR}/time_metrics.stacks"
      File.open(path, 'w') do |file|
        mean_by_stack.each_pair do |stack, mean|
          on_top_of_stack = mean
          mean_by_stack.each_pair do |sub_stack, time|
            on_top_of_stack -= time if sub_stack[0...-1] == stack
          end

          file.puts "#{stack.join(';')} #{on_top_of_stack.round}"
        end
      end
      sh "#{repo}/flamegraph.pl", '--flamechart', '--countname', 'ms', path, out: 'time_metrics_flamegraph.svg'
    end
  end

  private def get_times(trace, total)
    result = Hash.new(0)
    stack = [['total', 0]]

    result[stack.map(&:first)] = total
    result[%w[total jvm]] = 0

    trace.each_line do |line|
      if line =~ /^(.+) (\d+)$/
        region = $1
        time = Float($2)
        if region.start_with? 'before-'
          name = region['before-'.size..-1]
          stack << [name, time]
          result[stack.map(&:first)] += 0
        elsif region.start_with? 'after-'
          name = region['after-'.size..-1]
          prev, start = stack.last
          raise "#{region} after before-#{prev}" unless name == prev
          result[stack.map(&:first)] += (time - start)
          stack.pop
        else
          STDERR.puts line
        end
      # [Full GC (Metadata GC Threshold)  23928K->23265K(216064K), 0.1168747 secs]
      elsif line =~ /^\[(.+), (\d+\.\d+) secs\]$/
        name = $1
        time = Float($2)
        stack << [name, time]
        result[stack.map(&:first)] += (time * 1000.0)
        stack.pop
      # [0.423s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 25M->7M(506M) 5.526ms
      elsif line =~ /^\[\d+\.\d+s\]\[info\]\[gc\] (.+) (\d+\.\d+)ms$/
        name = $1
        time = Float($2)
        stack << [name, time]
        result[stack.map(&:first)] += time
        stack.pop
      elsif line =~ /\[info\]\[gc\] Using G1$/ or line =~ /\[info\]\[gc\] GC\(\d+\) Concurrent Cycle$/
        # ignore
      else
        STDERR.puts line
      end
    end

    result[%w[total jvm]] = total - result[%w[total main]]
    result
  end

  def benchmark(*args)
    vm_args = []
    if truffleruby?
      if truffleruby_compiler?
        vm_args << '--check-compilation'
      else
        STDERR.puts 'WARNING: benchmarking without the GraalVM compiler'
      end
    end

    args, ruby_args = args_split(args)

    if start_time_index = args.index('START_TIME_SET_BY_JT_BENCHMARK')
      args[start_time_index] = Process.clock_gettime(Process::CLOCK_MONOTONIC).to_s
    end

    run_ruby(*vm_args, *ruby_args, "#{TRUFFLERUBY_DIR}/bench/benchmark", *args, use_exec: true)
  end

  def profile(*args)
    require_ruby_launcher!
    env = args.first.is_a?(Hash) ? args.shift : {}
    stdin = args.delete '-'

    Dir.mkdir(PROFILES_DIR) unless Dir.exist?(PROFILES_DIR)

    time = Time.now.strftime('%Y%m%d-%H%M%S')
    suffix = "_#{ENV.fetch('JT_PROFILE_SUFFIX') { truffleruby? ? @ruby_name : 'cruby' } }"
    flame_file_prefix = "#{PROFILES_DIR}/flamegraph_#{time}#{suffix}"
    svg_filename = "#{flame_file_prefix}.svg"

    if stdin
      File.write(svg_filename, STDIN.read)
    else
      require 'benchmark'
      FileUtils.rm_f(svg_filename)
      run_args = if truffleruby?
                   DEFAULT_PROFILE_OPTIONS + ["--cpusampler.OutputFile=#{svg_filename}"] + args
                 else
                   flame_file = "#{flame_file_prefix}.html"
                   flame_file_dump = "#{flame_file_prefix}.dump"
                   ENV['STACKPROF_RAW'] = 'true'
                   ENV['STACKPROF_OUT'] = flame_file_dump

                   ['-rstackprof/autorun'] + args
                 end
      puts Benchmark.measure {
        run_ruby(env, *run_args)
      }
    end

    if truffleruby?
      app_open svg_filename
    else
      run_ruby(env, '-S', 'stackprof', '--d3-flamegraph', flame_file_dump, out: flame_file)
      app_open flame_file
    end
  end

  def graph(*args)
    truffleruby_compiler!

    test_file = nil
    method = 'Object#foo'
    watch = false
    simplify = false
    describe = false
    json = false
    cfg2asm = false
    seafoam_args = []
    cfg2asm_args = []

    # Must be handled before ruby_options() otherwise that would consume it
    igv = args.delete('--igv')
    args.unshift '--igv-network' if igv

    vm_args, remaining_args, _parsed_options = ruby_options({}, args)
    args = remaining_args
    app_args = []

    until args.empty?
      arg = args.shift
      case arg
      when '--json'
        json = true
      when '--method'
        raise if args.empty?
        method = args.shift
      when '--watch'
        watch = true
      when '--simplify'
        simplify = true
      when '--describe'
        describe = true
      when '--cfg2asm'
        cfg2asm = true
      when '--no-comments'
        cfg2asm_args << '--no-comments'
      when '--'
        seafoam_args = args.dup
        args.clear
      when /^-/
        vm_args << arg
      else
        test_file = arg
        if dash = args.index('--')
          app_args = [arg, *args[0...dash]]
          args = args[dash..-1]
        else
          app_args = [arg, *args]
          args.clear
        end
      end
    end

    raise unless test_file

    base_vm_args = [
      '--experimental-options',
      '--engine.TraceCompilation',
      '--engine.BackgroundCompilation=false',
      "--engine.CompileOnly=#{method}",
      '--engine.MultiTier=false',
      '--compiler.NodeSourcePositions',
      "--vm.D#{GRAAL_OPTION_PREFIX}.PrintGraphWithSchedule=true",
      *("--vm.D#{GRAAL_OPTION_PREFIX}.PrintBackendCFG=true" if cfg2asm),
      "--vm.D#{GRAAL_OPTION_PREFIX}.Dump=Truffle:1",
      '--log.file=/dev/stderr', # suppress the Truffle log output help message
    ]

    # As per https://github.com/Shopify/seafoam/blob/master/docs/getting-graphs.md
    # GR-36849: needs #truffleruby_native_built? instead of #truffleruby_native?
    simplify_vm_args = [
      *("--vm.D#{GRAAL_OPTION_PREFIX}.PartialUnroll=false" unless truffleruby_native_built?),
      *("--vm.D#{GRAAL_OPTION_PREFIX}.LoopPeeling=false" unless truffleruby_native_built?),
      *("--vm.D#{GRAAL_OPTION_PREFIX}.LoopUnswitch=false" unless truffleruby_native_built?),
      "--vm.D#{GRAAL_OPTION_PREFIX}.OptScheduleOutOfLoops=false",
    ]

    base_vm_args += simplify_vm_args if simplify
    vm_args = base_vm_args + vm_args

    loop do # for --watch
      compiled = false
      command = [ruby_launcher, *vm_args, *app_args]
      STDERR.puts bold "$ #{printable_cmd(command)}" if @verbose
      IO.popen(command, err: [:child, :out]) do |pipe|
        pipe.each_line do |line|
          puts line
          if line =~ /\[engine\] opt done(\s+engine=\d+)?\s+id=\d+\s+#{Regexp.escape(method)}/
            compiled = true
            Process.kill 'TERM', pipe.pid
          end
        end
      end
      raise "The process did not compile #{method}" unless compiled

      unless igv
        # See StandardPathUtilitiesProvider#sanitizeFileName in graal
        method_glob_pattern = method.gsub(/[ \/\p{Cntrl}]/, '_')
        if truffleruby_native?
          method_glob_pattern = "{Isolated:_,}#{method_glob_pattern}"
        end

        dumps = Dir.glob('graal_dumps/*').select { |path| File.directory?(path) }.sort.last
        raise 'Could not find dump directory under graal_dumps/' unless dumps
        graphs = Dir.glob("#{dumps}/*\\[#{method_glob_pattern}*\\].bgv").sort
        graph = graphs.last
        raise "Could not find graph in #{dumps}" unless graph

        FileUtils.cp graph, 'graph.bgv'
        graph = 'graph.bgv'

        if cfg2asm
          cfgs = Dir.glob("#{dumps}/*\\[#{method_glob_pattern}*\\].cfg").sort
          cfg = cfgs.last
          raise "Could not find CFG in #{dumps}" unless cfg

          FileUtils.cp cfg, 'cfg.cfg'
          cfg = 'cfg.cfg'
        end

        list = seafoam('--json', graph, 'list', capture: :out, no_print_cmd: true)
        decoded = JSON.parse(list)
        graph_names = decoded.map { |entry| entry.fetch('graph_name_components').last }
        before_lowering_regexp = /Before.+Lowering/
        before_lowering = graph_names.index { |name| name =~ before_lowering_regexp }
        raise "Could not find #{before_lowering_regexp.inspect} in #{graph_names}" unless before_lowering
        n = decoded.fetch(before_lowering).fetch('graph_index')

        json_args = json ? %w[--json] : []
        action = describe ? 'describe' : 'render'
        seafoam(*json_args, "#{graph}:#{n}", action, *seafoam_args)

        cfg2asm(cfg, *cfg2asm_args) if cfg2asm
      end

      break unless watch
      puts # newline between runs

      # Sorry... actively poll to avoid any dependencies
      time = File.mtime(test_file)
      sleep 1 until File.mtime(test_file) > time
    end
  end

  private def seafoam(*args)
    seafoam_dir = ENV['SEAFOAM_DIR']
    if seafoam_dir
      sh(RbConfig.ruby, "-I#{seafoam_dir}/lib", "#{seafoam_dir}/bin/seafoam", *args)
    else
      run_gem_or_install('seafoam', SEAFOAM_VERSION, *args)
    end
  end
  ruby2_keywords :seafoam if respond_to?(:ruby2_keywords, true)

  private def cfg2asm(*args)
    cfg2asm_dir = ENV['CFG2ASM_DIR']
    if cfg2asm_dir
      sh(RbConfig.ruby, "-I#{cfg2asm_dir}/lib", "#{cfg2asm_dir}/bin/cfg2asm", *args)
    else
      run_gem_or_install('cfg2asm', CFG2ASM_VERSION, *args)
    end
  end
  ruby2_keywords :cfg2asm if respond_to?(:ruby2_keywords, true)

  def igv
    compiler = "#{GRAAL_DIR}/compiler"
    ENV['TOOLS_JAVA_HOME'] = install_jvmci("Downloading JDK#{IGV_JDK_VERSION} to run IGV", jdk_version: IGV_JDK_VERSION)
    mx('igv', chdir: compiler)
  end

  def install(name, *options)
    case name
    when 'jvmci'
      puts install_jvmci("Downloading JDK#{@jdk_version} with JVMCI")
    when 'graalvm'
      puts install_graalvm
    when 'eclipse'
      puts install_eclipse
    else
      raise "Unknown how to install #{what}"
    end
  end

  private def install_jvmci(download_message, jdk_version: @jdk_version)
    raise "Unknown JDK version: #{jdk_version}" unless JDK_VERSIONS.include?(jdk_version)

    jdk_name = "labsjdk-ce-#{jdk_version}"
    # We try to match the default directory name that mx fetch-jdk uses here to avoid extra symlinks
    java_home = "#{JDKS_CACHE_DIR}/#{jdk_name}-#{jvmci_version}"
    unless File.directory?(java_home)
      STDERR.puts "#{download_message} (#{jdk_name})"
      mx '-y', 'fetch-jdk',
         '--configuration', graal_common_json,
         '--java-distribution', jdk_name,
         '--to', JDKS_CACHE_DIR,
         '--alias', java_home, # ensure the JDK ends up in the path we expect
         java_home: :none # avoid recursion
    end

    java_home = "#{java_home}/Contents/Home" if darwin?
    java = "#{java_home}/bin/java"
    abort "#{java} does not exist" unless File.executable?(java)

    java_home
  end

  private def install_graalvm
    version = BOOTSTRAP_GRAALVM_VERSION
    major = version[/^(\d+)/, 1]
    archive_version = version.sub(/\.0\.0$/, '')
    os = { 'linux' => 'linux', 'darwin' => 'macos' }.fetch(mx_os)
    arch = { 'amd64' => 'x64', 'aarch64' => 'aarch64' }.fetch(mx_arch)
    if ee?
      url = "https://download.oracle.com/graalvm/#{major}/archive/graalvm-jdk-#{archive_version}_#{os}-#{arch}_bin.tar.gz"
      dir = "#{JDKS_CACHE_DIR}/graalvm-#{version}"
    else
      url = "https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-#{version}/graalvm-community-jdk-#{version}_#{os}-#{arch}_bin.tar.gz"
      dir = "#{JDKS_CACHE_DIR}/graalvm-community-#{version}"
    end
    archive = "#{dir}.tar.gz"
    unless File.file?(archive)
      verbosity = STDOUT.tty? ? [] : ['--no-verbose']
      sh 'wget', *verbosity, '-O', archive, url
    end
    unless File.directory?(dir)
      Dir.mkdir(dir)
      sh 'tar', '--extract', '--file', archive, '--directory', dir, '--strip-components', '1'
    end
    dir
  end

  private def install_eclipse
    require 'digest'
    if linux?
      eclipse_exe = 'eclipse/eclipse'
      if aarch64?
        eclipse_url = 'https://github.com/truffleruby/eclipse-mirror/releases/download/eclipse-SDK-4.26/eclipse-4.26.0-linux-aarch64.tar.gz'
        sha256 = '6cbf7ef69206739f1f11e2883b78ecb9b0a6254400bce51ce5c0c5c2cd550a43'
      elsif amd64?
        eclipse_url = 'https://github.com/truffleruby/eclipse-mirror/releases/download/eclipse-SDK-4.26/eclipse-4.26.0-linux-amd64.tar.gz'
        sha256 = '5a71af895af325de533e175289983a023167183ebdcc4cb7d8b3b01420d5b8c0'
      else
        raise 'Only AARCH64 and AMD64 are supported for Eclipse on Linux'
      end
    elsif darwin?
      eclipse_exe = 'Eclipse.app/Contents/MacOS/eclipse'
      if aarch64?
        eclipse_url = 'https://github.com/truffleruby/eclipse-mirror/releases/download/eclipse-SDK-4.26/eclipse-4.26.0-darwin-aarch64.dmg'
        sha256 = 'fcabac1b5a7c49ac674bd0f6273f5a93007261d3f7a0296473be9295d9424e00'
      elsif amd64?
        eclipse_url = 'https://github.com/truffleruby/eclipse-mirror/releases/download/eclipse-SDK-4.26/eclipse-4.26.0-darwin-amd64.dmg'
        sha256 = '9f26d17e3633085aaac2b97212205492134ae4e5497cadb9957c2bb6c4b63a59'
      else
        raise 'Only AARCH64 and AMD64 are supported for Eclipse on macOS'
      end
    else
      raise 'Installing Eclipse is only available on Linux and macOS currently'
    end

    eclipse_package = eclipse_url.split('/').last
    eclipse_name = File.basename(eclipse_package, linux? ? '.tar.gz' : '.dmg')
    eclipse_path = "#{CACHE_EXTRA_DIR}/#{eclipse_name}/#{eclipse_exe}"
    return eclipse_path if File.exist?(eclipse_path)

    chdir(CACHE_EXTRA_DIR) do
      unless File.exist?(eclipse_package)
        sh 'curl', '-L', eclipse_url, '-o', eclipse_package
      end

      unless File.exist?(eclipse_name)
        computed = Digest::SHA256.file(eclipse_package).hexdigest

        if computed == sha256
          Dir.mkdir eclipse_name

          if linux?
            # extract tar archive into eclipse_name directory
            sh 'tar', 'xf', eclipse_package, '-C', eclipse_name
          else # darwin
            # mount macOS volume into /Volumes/Eclipse (default) and copy files into eclipse_name directory
            sh 'hdiutil', 'attach', '-readonly', '-nobrowse', eclipse_package
            sh 'cp', '-rf', '/Volumes/Eclipse/Eclipse.app', eclipse_name
            sh 'hdiutil', 'detach', '/Volumes/Eclipse'
          end
        else
          raise "Incorrect sha256 for #{eclipse_package}: #{computed} instead of expected #{sha256}"
        end
      end
    end

    eclipse_path
  end

  private def sforceimports?(mx_base_args)
    return true unless File.directory?(GRAAL_DIR)

    scheckimports_output = mx(*mx_base_args, 'scheckimports', '--ignore-uncommitted', '--warn-only', java_home: :none, primary_suite: TRUFFLERUBY_DIR, capture: :both)

    unless scheckimports_output.empty?
      # Don't ask to update, just warn.
      if ENV['JT_IMPORTS_DONT_ASK'] || !STDIN.tty?
        with_color(TERM_COLOR_RED) do
          boxed do
            puts scheckimports_output
            puts <<~MESSAGE
              You might want to:
              * use the version of graal in suite.py, then use "jt build --sforceimports", useful when building TruffleRuby and not changing graal at the same time (most common)
              * update the graal version in suite.py, then use "mx scheckimports", useful when explicitly updating the default graal version of TruffleRuby (rare)
              * test TruffleRuby with a different graal version with your own changes in graal, then you can ignore this warning
            MESSAGE
          end
        end
        false
      else
        # Ask to update imports.
        with_color(TERM_COLOR_RED) do
          puts "\nNOTE: Set env variable JT_IMPORTS_DONT_ASK to always answer 'no' to this prompt.\n\n"
          puts scheckimports_output
          begin
            print 'Do you want to checkout the supported version of graal as specified in truffleruby\'s suite.py? (runs `mx sforceimports`) [y/n] '
            STDOUT.flush
            input = STDIN.gets.chomp
          end until %w(y n).include? input
          puts ''
          input == 'y'
        end
      end
    end
  end

  def sforceimports
    mx('sforceimports', java_home: :none, primary_suite: TRUFFLERUBY_DIR)
  end

  def show_available_memory
    if linux?
      STDERR.puts `free -m`
    elsif darwin?
      STDERR.puts `memory_pressure`.lines.grep(/The system has|System-wide memory free percentage/)
    end
  end

  private def build_standalone(*options)
    if options.delete('--new-hash')
      build_information_path = "#{TRUFFLERUBY_DIR}/src/shared/java/org/truffleruby/shared/BuildInformation.java"
      raise unless File.exist?(build_information_path) # in case the file moves in the future
      FileUtils.touch(build_information_path)
    end

    # Override @mx_env if --env is passed
    if (i = options.index('--env') || options.index('-e'))
      options.delete_at i
      @mx_env = options.delete_at i
    end
    env = @mx_env

    # Override @ruby_name from --name or --env
    if (i = options.index('--name') || options.index('-n'))
      options.delete_at i
      @ruby_name = options.delete_at i
    else
      @ruby_name = env
    end

    name = "truffleruby-#{@ruby_name}"
    mx_base_args = ['--env', env]

    os_env = {
      'BOOTSTRAP_GRAALVM' => install_graalvm
    }

    if options.delete('--sforceimports') || sforceimports?(mx_base_args)
      sforceimports
    end

    if ENV['CI']
      STDERR.puts 'Memory available before starting the build:'
      show_available_memory
    end


    if ENV['GITHUB_ACTIONS'] and darwin? and aarch64?
      # There is only 7GB of RAM on darwin-arm64 on GitHub Actions:
      # https://docs.github.com/en/actions/reference/runners/github-hosted-runners
      # So we need to tell the Native Image Driver to use everything to have enough memory to build the image
      options = ['--extra-image-builder-argument=rubyvm:-J-XX:MaxRAMPercentage=100', '--extra-image-builder-argument=rubyvm:--parallelism=2', *options]
    end

    mx_options, mx_build_options = args_split(options)
    mx_args = mx_base_args + mx_options

    mx(os_env, *mx_args, 'build', *mx_build_options, primary_suite: TRUFFLERUBY_DIR)

    standalone_dist = env.include?('native') ? 'TRUFFLERUBY_NATIVE_STANDALONE' : 'TRUFFLERUBY_JVM_STANDALONE'
    build_dir = "#{TRUFFLERUBY_DIR}/mxbuild/#{mx_platform}/#{standalone_dist}"

    # Copy to mxbuild/$name. We need to copy and not symlink because e.g. CE/EE use the same standalone_dist (GR-53433)
    dest = "#{TRUFFLERUBY_DIR}/mxbuild/#{name}"
    FileUtils.rm_rf dest
    FileUtils.cp_r(build_dir, dest)

    # Symlink builds into version manager
    rbenv_root = ENV['RBENV_ROOT']
    rubies_dir = File.join(rbenv_root, 'versions') if rbenv_root && File.directory?(rbenv_root)

    chruby_versions = File.expand_path('~/.rubies')
    rubies_dir = chruby_versions if File.directory?(chruby_versions)

    if rubies_dir
      Dir.glob(rubies_dir + '/truffleruby-*').each do |link|
        next unless File.symlink?(link)
        next if File.exist?(link)
        target = File.readlink(link)
        next unless target.start_with?("#{TRUFFLERUBY_DIR}/mxbuild")
        File.delete link
        puts "Deleted broken link: #{link} -> #{target}"
      end

      link_path = "#{rubies_dir}/#{name}"
      File.delete link_path if File.symlink? link_path or File.exist? link_path
      File.symlink dest, link_path
    end
  end

  def build_release_archives
    if darwin?
      ENV['MACOSX_DEPLOYMENT_TARGET'] = '11.0'
    end

    # Build both CE standalones, then both EE standalones to avoid extra rebuilds of layout distributions
    jt(*%w[build --env jvm-ce -- --dep TRUFFLERUBY_JVM_STANDALONE_RELEASE_ARCHIVE])
    jt(*%w[build --env native -- --dep TRUFFLERUBY_NATIVE_STANDALONE_RELEASE_ARCHIVE])
    jt(*%w[build --env jvm-ee -- --dep TRUFFLERUBY_JVM_STANDALONE_RELEASE_ARCHIVE])
    jt(*%w[build --env native-ee -- --dep TRUFFLERUBY_NATIVE_STANDALONE_RELEASE_ARCHIVE])
    jt(*%w[build_platform_dependent_archive])
  end

  def build_release_archives_with_old_glibc
    build_in_docker_with_old_glibc('build_release_archives')
  end

  def build_platform_dependent_archive
    mx 'build', '--dep', 'RUBY_POM'

    FileUtils.mkdir_p('release')
    archive = "release/platform-dependent-layouts-#{mx_platform}.tar.gz"
    mx 'archive-pd-layouts', archive
    sh 'tar', 'tvf', archive
  end

  private def platform_dependent_archives
    # Expect all platforms except the current one since it will be rebuilt anyway
    (ALL_PLATFORMS - [mx_platform]).map do |platform|
      archive = "release/platform-dependent-layouts-#{platform}.tar.gz"
      raise "Missing #{archive}" unless File.exist?(archive)
      archive
    end
  end

  def build_maven_bundle
    in_truffleruby_repo_root!

    platform_dependent_archives.each do |archive|
      mx 'restore-pd-layouts', archive
    end

    mx("--multi-platform-layout-directories=#{ALL_PLATFORMS.join(',')}", 'ruby_maven_deploy_public')

    sh(*%W[tar czf ../release/maven-bundle.tar.gz --owner=0 --group=0], *Dir.children('maven-repo'), chdir: 'maven-repo')
  end

  def build_maven_bundle_with_old_glibc
    build_in_docker_with_old_glibc('build_maven_bundle', platform_dependent_archives)
  end

  def sign_maven_bundle(maven_bundle_tar_gz)
    raise unless File.file?(maven_bundle_tar_gz)
    maven_bundle_tar_gz = File.expand_path(maven_bundle_tar_gz)
    dir = File.dirname(maven_bundle_tar_gz)
    signed_dir = "#{dir}/maven-bundle-signed"
    FileUtils.rm_rf(signed_dir)
    FileUtils.mkdir_p(signed_dir)
    sh 'tar', 'xf', maven_bundle_tar_gz, chdir: signed_dir
    Dir.chdir(signed_dir) do
      Dir['**/*.{pom,jar}'].sort.each do |file|
        sh 'gpg', '-ab', file
      end
    end
    sh(*%W[tar czf ../maven-bundle-signed.tar.gz --owner=0 --group=0], *Dir.children(signed_dir), chdir: signed_dir)
  end

  def build_in_docker_with_old_glibc(command, files_to_copy_in = [])
    in_truffleruby_repo_root!

    dir = "#{TRUFFLERUBY_DIR}/tool/build-with-old-glibc"
    release_dir = "#{dir}/release"
    FileUtils.rm_rf(release_dir)
    FileUtils.mkdir_p(release_dir)
    FileUtils.touch("#{dir}/release/let_globbing_work")
    files_to_copy_in.each do |file|
      FileUtils.cp file, release_dir
    end

    sh 'docker', 'build',
      '--tag=truffleruby-with-old-glibc',
      '--build-arg', "GIT_COMMIT=#{git_commit}",
      '--build-arg', "JT_COMMAND=#{command}",
      '.', chdir: dir

    sh 'docker', 'create', '--name', 'trcopycontainer', 'truffleruby-with-old-glibc'
    begin
      sh 'docker', 'cp', 'trcopycontainer:/release', '.'
    ensure
      sh 'docker', 'rm', 'trcopycontainer'
    end
  end

  def next(*args)
    puts `cat spec/tags/core/**/**.txt | grep 'fails:'`.lines.sample
  end

  def rubocop(*args)
    in_truffleruby_repo_root!
    if args.empty? or args.all? { |arg| arg.start_with?('-') }
      args += RUBOCOP_INCLUDE_LIST
    end

    env = { **ruby_running_jt_env, 'BUNDLE_GEMFILE' => 'tool/rubocop-bundle/Gemfile' }
    sh env, 'bundle', 'install'
    sh env, 'bundle', 'exec', 'rubocop', *args
  end

  def idea(*args)
    ENV['ECLIPSE_EXE'] ||= install_eclipse
    if args.empty?
      args = %w[--dy /substratevm,/tools,/vm]
    end
    mx(*args, 'intellijinit', '--import-inner-classes')
  end

  def command_format(changed_java_files = nil)
    STDERR.puts bold '$ jt format'
    ENV['ECLIPSE_EXE'] ||= install_eclipse
    if changed_java_files.is_a?(Array)
      File.write('mxbuild/javafilelist.txt', changed_java_files.join("\n"))
      filelist_args = %w[--filelist mxbuild/javafilelist.txt]
    end
    env = { '_JAVA_OPTIONS' => '-Djava.net.preferIPv4Stack=true' }
    mx env, 'eclipseformat', '--no-backup', '--primary', *filelist_args
    format_specializations_check
  end

  def check_filename_length
    # For eCryptfs, see https://bugs.launchpad.net/ecryptfs/+bug/344878
    max_length = 143

    too_long = []
    Dir.chdir(TRUFFLERUBY_DIR) do
      Dir.glob('**/*') do |f|
        if !f.start_with?('graal_dumps/')
          if File.basename(f).size > max_length
            too_long << f
          end
        end
      end
    end

    unless too_long.empty?
      abort "Too long filenames for eCryptfs:\n#{too_long.join "\n"}"
    end
  end

  private def check_core_symbols
    build('core-symbols')
    diff = sh 'git', 'diff', 'src/main/java/org/truffleruby/core/symbol/CoreSymbols.java', capture: :out
    unless diff.empty?
      STDERR.puts 'DIFF:'
      STDERR.puts diff
      abort "CoreSymbols.java must be regenerated by 'jt build core-symbols'"
    end
  end

  def check_heap_dump(*args)
    common_args = ['--lang', 'ruby',
                   '--forbidden-class', 'org.truffleruby.language.RubyDynamicObject',
                   '--forbidden-class', 'org.truffleruby.RubyContext']
    args.unshift(*common_args)
    mx 'ruby_check_heap_dump', '--shared-engine', *args, '--code', "p 42; p require 'set'; p require 'openssl'"
  end

  private def check_options
    build('options')
    ['Options.java', 'LanguageOptions.java'].each do |file|
      diff = sh 'git', 'diff', "src/main/java/org/truffleruby/options/#{file}", capture: :out
      unless diff.empty?
        STDERR.puts 'DIFF:'
        STDERR.puts diff
        abort "#{file} must be regenerated by 'jt build options'"
      end
    end
  end

  def check_polyglot_methods
    run_ruby('tool/generate-polyglot-methods.rb')
    diff = sh 'git', 'diff', 'src/main/ruby/truffleruby/core/truffle/polyglot_methods.rb', capture: :out
    unless diff.empty?
      STDERR.puts 'DIFF:'
      STDERR.puts diff
      abort "polyglot_methods.rb must be regenerated by 'tool/generate-polyglot-methods.rb'"
    end
  end

  private def check_source_files
    diff = sh 'git', 'diff', capture: :out
    unless diff.empty?
      STDERR.puts 'DIFF:'
      STDERR.puts diff
      abort 'Source files should not be modified in CI'
    end
  end

  def native_configuration_file
    os = mx_os.capitalize
    arch = { 'amd64' => 'AMD64', 'aarch64' => 'AArch64' }.fetch(mx_arch)
    puts "src/main/java/org/truffleruby/platform/#{os}#{arch}NativeConfiguration.java"
  end

  def check_native_configuration
    files = Dir.glob('src/main/java/org/truffleruby/platform/*NativeConfiguration.java')
    diff = sh 'git', 'diff', *files, capture: :out
    unless diff.empty?
      STDERR.puts 'DIFF:'
      STDERR.puts diff
      abort "*NativeConfiguration.java must be regenerated by 'ruby tool/generate-native-config.rb'"
    end
  end

  def config_header_file
    puts "lib/cext/include/truffleruby/config_#{mx_os}_#{mx_arch}.h"
  end

  def check_config_header
    files = Dir.glob('lib/cext/include/truffleruby/config_*.h')
    diff = sh 'git', 'diff', *files, capture: :out
    unless diff.empty?
      STDERR.puts 'DIFF:'
      STDERR.puts diff
      abort "lib/cext/include/truffleruby/config_*.h must be regenerated by 'tool/generate-config-header.sh'"
    end
  end

  def check_documentation
    status = true
    allowed = -> str {
      str.ascii_only? or str.chars.all? { |c| c.ascii_only? or %w[± – → ⌥ ⌘ ⇧ ├ └ ─ │ ∅].include?(c) }
    }

    `git -C #{TRUFFLERUBY_DIR} ls-files '**/*.md'`.lines.map(&:chomp).each do |file|
      next if file.start_with?('lib/gems/')
      contents = File.read(file, encoding: Encoding::UTF_8)
      unless allowed[contents]
        non_ascii = contents.lines.reject(&allowed).map do |line|
          line.chars.map { |c| allowed[c] ? c : "[#{c}]" }.join
        end.join
        status = false
        puts "Non-US-ASCII character in #{file}:\n#{non_ascii}"
      end
    end
    abort unless status
  end

  def check_documentation_urls
    url_base = 'https://github.com/truffleruby/truffleruby/blob/master/doc/'
    # Explicit list of URLs, so they can be added manually
    # Notably, Ruby installers reference the LLVM urls
    known_hardcoded_urls = %w[
      https://github.com/truffleruby/truffleruby/blob/master/doc/user/installing-libssl.md
      https://github.com/truffleruby/truffleruby/blob/master/doc/user/installing-llvm.md
      https://github.com/truffleruby/truffleruby/blob/master/doc/user/installing-zlib.md
      https://github.com/truffleruby/truffleruby/blob/master/doc/user/ruby-managers.md
    ]

    known_hardcoded_urls.each do |url|
      file = url[url_base.size..-1]
      path = "#{TRUFFLERUBY_DIR}/doc/#{file}"
      unless File.file?(path)
        abort "#{path} could not be found but is referenced in code"
      end
    end

    hardcoded_urls = `git -C #{TRUFFLERUBY_DIR} grep -Fn #{url_base.inspect}`
    status = true
    hardcoded_urls.each_line do |line|
      abort "Could not parse #{line.inspect}" unless /(.+?):(\d+):.+?(https:.+?)(#[\w-]+)?[ "'\n]/ =~ line
      file, line, url = $1, $2, $3
      if !%w[tool/jt.rb].include?(file) and !known_hardcoded_urls.include?(url)
        puts "Found unknown hardcoded url #{url} in #{file}:#{line}, add it in tool/jt.rb"
        status = false
      end
    end
    abort unless status
  end

  def shellcheck
    files = sh 'git', 'ls-files', capture: :out, no_print_cmd: true
    sh_files = files.lines.map(&:chomp).select { |f| f.end_with?('.sh') }
    puts bold '$ shellcheck -a -x ...'
    sh 'shellcheck', '-a', '-x', *sh_files, no_print_cmd: true
  end

  def check_license
    v = '1.0' # to avoid self-matching
    ["Eclipse Public License version #{v}", "Eclipse Public License #{v}", "EPL #{v}", "EPL#{v}", "EPL-#{v}"].each do |match|
      output = `git -C #{TRUFFLERUBY_DIR} grep '#{match}'`
      output = output.lines.reject { |line| line.start_with?('lib/mri/rubygems/util/licenses.rb:') }.join
      unless output.empty?
        abort "There should be no mention of #{match} in the repository:\n#{output}"
      end
    end
  end

  def checkstyle(changed_java_files = nil)
    if changed_java_files.is_a?(Array)
      File.write('mxbuild/javafilelist.txt', changed_java_files.join("\n"))
      filelist_args = %w[--filelist mxbuild/javafilelist.txt]
    end
    output = mx 'checkstyle', '--primary', *filelist_args, capture: :both, continue_on_failure: true
    status = $?

    unused_import = /: Unused import -/
    if !status.success? and output =~ unused_import
      puts 'Automatically removing unused imports'
      output.lines.reverse.grep(unused_import) do |line|
        path, lineno, _ = line.split(':', 3)
        lineno = Integer(lineno)

        puts "Removing unused import in #{path}:#{lineno}"
        lines = File.readlines path
        lines.delete_at(lineno-1)
        File.write path, lines.join
      end

      # Still error out as the removed imports should be committed, but mark them as fixed
      output = output.lines.map { |line| line =~ unused_import ? "[FIXED] #{line}" : line }.join
      puts
    end

    STDERR.puts output
    unless status.success?
      exit status.exitstatus
    end
  end

  module Formatting
    extend self

    def format_specializations_visibility
      iterate do |type, (first, *rest)|
        if type == :ExportMessage and first !~ /\bstatic\b/
          # Keep non-static @ExportMessage public
          [first, *rest]
        else
          # Removes visibility modifier
          [first.gsub(/^( *)(public |protected |private |)/, '\1'), *rest]
        end
      end
    end

    def format_specializations_arguments
      iterate do |_type, lines|
        first = lines.first
        indent = "#{first[/^ +/]}"
        arg_indent = indent + ' ' * 8
        declaration, *arguments, rest = split_arguments(lines.join)

        dynamic_arguments, cached_arguments = arguments.partition { |segment| segment !~ /^ *@/ || segment =~ /^ *@SuppressWarnings/ }

        if cached_arguments.empty?
          tail = rest
        else
          tail = dynamic_arguments.empty? ? '' : ','
        end
        one_line = indent + declaration + dynamic_arguments.join(', ') + tail
        one_line_below = arg_indent + dynamic_arguments.join(', ') + tail

        if one_line.size <= 120
          [one_line + "\n",
           *cached_arguments[0..-2].map { |c| arg_indent + c + ",\n" },
           *(arg_indent + cached_arguments[-1] + rest + "\n" unless cached_arguments.empty?)]
        elsif one_line_below.size <= 120
          ["#{indent}#{declaration}\n",
           one_line_below + "\n",
           *cached_arguments[0..-2].map { |c| arg_indent + c + ",\n" },
           *(arg_indent + cached_arguments[-1] + rest + "\n" unless cached_arguments.empty?)]
        else
          [indent + declaration + "\n",
           *arguments[0..-2].map { |c| arg_indent + c + ",\n" },
           arg_indent + arguments[-1] + rest + "\n"]
        end
      end
    end

    def format_imports
      each_file do |content|
        content
          .sub(/\n{3,}import /, "\n\nimport ")
          .sub(/^(import .+;)\n{3,}((?:(?:@|\/\/|\/\*).+\n)*)public/, "\\1\n\n\\2public")
      end
    end

    def final_classes
      generics = /(?:<[A-Z,]+>)?/
      class_regexp = /^\s*([ a-z]*)class (\w+)\b#{generics}(?: extends (\w+\.)?(\w+)\b#{generics})?.*\{$/

      subclasses = {}
      each_file('main') do |content|
        content.scan(class_regexp) do
          modifiers, name, superclass_prefix, superclass = $1.strip.split, $2, $3, $4
          raise "superclass prefix not supported for: #{$&}" if superclass_prefix
          if superclass
            (subclasses[superclass] ||= []) << name
          end
          unless modifiers.include?('abstract') or modifiers.include?('final')
            subclasses[name] ||= []
          end
        end
        content
      end

      should_be_final = subclasses.select { |_name, subs| subs.empty? }
      return false if should_be_final.empty?

      each_file('main') do |content|
        new_content = content
        content.scan(class_regexp) do
          modifiers, name = $1.strip.split, $2
          line = $&
          unless modifiers.include?('abstract') or modifiers.include?('final')
            if should_be_final.include?(name)
              new_content = new_content.sub(line, line.sub(/\bclass #{name}\b/, "final class #{name}"))
            end
          end
        end
        new_content
      end
    end

    # This is a good way to be notified when new node types are added in Prism,
    # and helps to ensure we handle all nodes.
    def all_visit_methods_in_yarp_translator
      visit_methods = File.read('src/yarp/java/org/prism/AbstractNodeVisitor.java').scan(/\bpublic T (visit\w+)\(/).map(&:first)
      overridden_methods = File.read('src/main/java/org/truffleruby/parser/YARPTranslator.java').scan(/\bpublic \w*Node (visit\w+)\(/).map(&:first)
      missing = visit_methods - overridden_methods
      if missing.empty?
        false
      else
        STDERR.puts "Missing methods in YARPTranslator: #{missing}"
        true
      end
    end

    private

    def split_arguments(line)
      i = 0
      split_points = []
      i += 1 while line[i] != '('
      i += 1
      parens = 1
      generic = 0
      split_points << i # ( start of arguments


      while i < line.size
        case line[i]
        when '('
          parens += 1
        when ')'
          parens -= 1
          if parens == 0
            split_points << i
            break
          end
        when '<'
          generic += 1
        when '>'
          generic -= 1
        when ','
          split_points << i if parens == 1 && generic == 0
        else
          # nothing to do
        end
        i += 1
      end

      split_points = [0, *split_points, -1]
      segments = split_points.each_with_index.reduce([]) do |arr, (split_point, j)|
        if j + 1 == split_points.size
          arr
        else
          arr << line[split_point...split_points[j + 1]]
        end
      end
      segments.map { |segment| segment.gsub(/\A,?\s+|\s+,?\Z/, '') }
    end

    def each_file(subdirs = [])
      changed = false
      Dir.glob(File.join(TRUFFLERUBY_DIR, 'src', *subdirs, '**', '*.java')) do |file|
        content = File.read file
        new_content = yield content

        if content != new_content
          puts "#{file} updated"
          changed = true
          File.write file, new_content
        end
      end
      changed
    end

    def iterate(&update)
      each_file do |content|
        new_content = +''
        lines = content.lines.to_a

        while (line = lines.shift)
          if /^ *@(Specialization|Fallback|CreateCast|ExportMessage)/ =~ line
            type = $1.to_sym
          else
            new_content << line
            next
          end

          braces = count_braces(line)
          # look for end of annotation
          while braces != 0
            new_content << line
            line = lines.shift
            braces += count_braces(line)
          end

          # Never look for a declaration on the same line as the end of the annotation,
          # as the regex below can cause false positives.
          new_content << line
          line = lines.shift

          # look for declaration
          while line !~ /[\w<>]+(\[\])? \w+\(/
            new_content << line
            line = lines.shift
          end

          # look for whole declaration
          braces = count_braces(line)
          declaration = [line]
          while braces != 0
            # new_content << line
            line = lines.shift
            braces += count_braces(line)
            declaration << line
          end

          declaration = update.call type, declaration
          declaration.each { |l| new_content << l }
        end

        new_content
      end
    end

    def count_braces(line, brackets = '()')
      line.count(brackets[0]) - line.count(brackets[1])
    end
  end

  def format_specializations_visibility
    Formatting.format_specializations_visibility
  end

  def format_specializations_arguments
    Formatting.format_specializations_arguments
  end

  def final_classes
    Formatting.final_classes
  end

  def all_visit_methods_in_yarp_translator
    Formatting.all_visit_methods_in_yarp_translator
  end

  def format_specializations_check
    any_failed = false
    [
      [format_specializations_visibility, 'Some Specializations used public/protected/private visibility modifier.'],
      [format_specializations_arguments, 'Some Specializations were not properly formatted.'],
      [Formatting.format_imports, 'There were extra blank lines around imports.'],
      [final_classes, 'There were classes which should be marked as final but were not.'],
      [all_visit_methods_in_yarp_translator, 'YARPTranslator should override all visit* methods from AbstractNodeVisitor.']
    ].each do |changed, error_message|
      any_failed ||= changed
      $stderr.puts error_message if changed
    end
    abort if any_failed
  end

  def check_generated_files
    check_core_symbols
    check_options
    check_polyglot_methods
  end

  def check_lockfiles(changed_lock_files = nil)
    unless changed_lock_files.is_a?(Array)
      changed_lock_files = `git -C #{TRUFFLERUBY_DIR} ls-files '**/*.lock'`.lines.map(&:chomp)
    end
    changed_lock_files.each do |file|
      contents = File.read(file)
      if contents.include?('BUNDLED WITH')
        abort "#{file} should not contain a BUNDLED WITH section to avoid needing to update this file when updating Bundler, and to ensure the Bundler version shipped with TruffleRuby is used.\nRemove the BUNDLED WITH section manually to solve this."
      end
    end
  end

  def lint(*args)
    in_truffleruby_repo_root!
    fast = args.first == 'fast'
    args.shift if fast

    if fast and compare_to = args.shift
      changed_files = changed_files(compare_to)
      changed = {}
      changed_files.each do |file|
        changed.fetch(File.extname(file)) { |k| changed[k] = [] } << file
      end
    else
      changed = -> _ext { true }
    end

    ENV['ECLIPSE_EXE'] ||= install_eclipse

    check_filename_length unless fast

    # Lint
    rubocop if changed['.rb']
    sh ruby_running_jt_env, 'tool/c-linter.rb' if changed['.c']
    checkstyle(changed['.java']) if changed['.java']
    command_format(changed['.java']) if changed['.java']
    shellcheck if changed['.sh'] or changed['.inc']
    check_lockfiles(changed['.lock']) if changed['.lock']

    mx 'verify-ci' if changed['.py'] and !ENV['JT_IMPORTS_DONT_ASK']

    check_abi(fail: !compare_to)

    unless fast
      mx 'gate', '--tags', 'style' # mx eclipseformat, mx checkstyle and a few more checks

      build

      check_generated_files

      check_documentation
      check_documentation_urls
      check_license

      check_source_files if ENV['CI']
      check_heap_dump if ENV['CI']

      run_ruby('tool/find_unused_primitives.rb')

      spotbugs
    end
  end

  ABI_VERSION_FILE = 'lib/cext/include/truffleruby/truffleruby-abi-version.h'
  ABI_CHECK_FILE = 'lib/cext/ABI_check.txt'

  def check_abi(fail: true)
    puts bold '$ jt check_abi'
    # Check since the last commit at which ABI_CHECK_FILE or ABI_VERSION_FILE were modified
    base_commit = `git log -n 1 --format=%H #{ABI_VERSION_FILE} #{ABI_CHECK_FILE}`.chomp

    changed_files = changed_files(base_commit)
    # All files which can affect the ABI of libtruffleruby.so
    abi_files = %w[
      lib/truffle/rbconfig.rb
      lib/mri/mkmf.rb
      lib/cext/include/**/*.{h,hpp}
      src/main/c/cext/extconf.rb
      src/main/c/cext/*.{c,h}
    ].flat_map { |pattern| Dir[pattern].sort }
    abi_files.delete ABI_VERSION_FILE

    changed_abi_files = changed_files & abi_files
    unless changed_abi_files.empty?
      puts 'These files have changed and might have affected the ABI:'
      puts changed_abi_files
      puts
      if changed_files.include?(ABI_VERSION_FILE)
        puts "#{ABI_VERSION_FILE} was updated to use a new ABI version"
      elsif changed_files.include?(ABI_CHECK_FILE)
        puts "#{ABI_CHECK_FILE} was updated, so ABI was marked as compatible"
      else
        puts 'Check the diff of this PR, and:'
        puts "* if ABI has changed, then increment #{ABI_VERSION_FILE}"
        puts "* if ABI has not changed, then increment #{ABI_CHECK_FILE}"
        puts
        puts 'Changing headers or compilation flags, as well as removing/adding a non-static function'
        puts '(because e.g. mkmf have_func can depend on that) should all be considered ABI changes.'
        if fail
          abort
        else
          warn 'WARNING: fix the above before creating the PR'
        end
      end
    end
  end

  private def changed_files(base_commit)
    `git diff --cached --name-only #{base_commit}`.lines.map(&:chomp)
  end

  def spotbugs
    mx 'ruby_spotbugs'
  end

  def sync
    exec(RbConfig.ruby, "#{TRUFFLERUBY_DIR}/tool/sync.rb", ruby_home)
  end

  def docker(*args)
    raise 'Ruby 3+ needed for "jt docker"' unless RUBY_VERSION.start_with?('3.')
    require_relative 'docker'
    JT::Docker.new.docker(*args)
  end

  private

  def load_excluded_file_names
    File.readlines("#{TRUFFLERUBY_DIR}/test/mri/failing.exclude").
      map { |line| line.gsub(/#.*/, '').strip }.reject(&:empty?)
  end
end

class JT
  include Commands

  def self.ruby(*args)
    jt = JT.new
    jt.send(:run_ruby, *args)
  end

  def initialize
    @silent = false
    @verbose = false
    @jdk_version = ENV['JT_JDK'] || DEFAULT_JDK_VERSION
    @ruby_name = ENV['RUBY_BIN'] || ENV['JT_ENV'] || 'jvm'
    @mx_env = ENV['JT_ENV'] || 'jvm'
  end

  def process_pre_args(args)
    needs_build = false
    needs_rebuild = false

    until args.empty?
      arg = args.shift
      case arg
      when '--build'
        needs_build = true
      when '--rebuild'
        needs_rebuild = true
      when '-u', '--use'
        @ruby_name = args.shift
      when '-q', '--silent'
        @silent = true
      when '-v', '--verbose'
        @verbose = true
      when '--jdk'
        @jdk_version = args.shift
      when '-h', '-help', '--help'
        help
        exit
      else
        args.unshift arg
        break
      end
    end

    raise "Invalid JDK version: #{@jdk_version}. Valid values: #{JDK_VERSIONS.join(' or ')}" unless JDK_VERSIONS.include?(@jdk_version)

    if needs_rebuild
      rebuild
    elsif needs_build
      build
    end
  end

  def main(args)
    args = args.dup
    process_pre_args(args)

    commands = Commands.public_instance_methods(false).map(&:to_s)

    command, *rest = args
    command ||= 'help'
    command = "command_#{command}" if %w[p puts format].include? command

    abort "no command matched #{command.inspect}" unless commands.include?(command)

    begin
      send(command, *rest)
    rescue
      puts "Error during command: #{args*' '}"
      raise $!
    end
  end
end

if $0 == __FILE__
  JT.new.main(ARGV)
end
