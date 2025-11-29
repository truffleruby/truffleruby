# Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'
require 'benchmark'
require 'json'

describe "The launcher" do
  versions = JSON.parse(File.read(File.expand_path('../../../versions.json', __FILE__)))
  @rubygems_version = versions.fetch('rubygems')
  gem_versions = versions.fetch('gems')
  @default_gems = gem_versions.fetch('default')
  @bundled_gems = gem_versions.fetch('bundled')

  escape = -> string { /^#{Regexp.escape string}$/ }
  @launchers = {
    bundle: escape["Bundler version #{@default_gems['bundler']}"],
    bundler: escape["Bundler version #{@default_gems['bundler']}"],
    erb: escape[@default_gems['erb']],
    gem: escape[@rubygems_version],
    irb: escape["irb #{@default_gems['irb']} (2024-12-18)"],
    racc: escape["racc version #{@bundled_gems['racc']}"],
    rake: escape["rake, version #{@bundled_gems['rake']}"],
    rbs: escape["rbs #{@bundled_gems['rbs']}"],
    rdbg: escape["rdbg #{@bundled_gems['debug']}"],
    rdoc: escape[@default_gems['rdoc']],
    ri: escape["ri #{@default_gems['rdoc']}"],
    ruby: /^truffleruby .* like ruby #{Regexp.escape RUBY_VERSION}/,
    syntax_suggest: escape["syntax_suggest #{@default_gems['syntax_suggest']}"],
    truffleruby: /^truffleruby .* like ruby #{Regexp.escape RUBY_VERSION}/,
  }

  before :all do
    @bindir = RbConfig::CONFIG['bindir']
  end

  before :each do
    @gem_home = ENV['GEM_HOME']
    ENV['GEM_HOME'] = nil
    @stderr = tmp("stderr")
    @redirect = "2>#{@stderr}"
  end

  after :each do
    ENV['GEM_HOME'] = @gem_home
    rm_r @stderr
  end

  def check_status_and_empty_stderr
    status = $?

    File.should.exist?(@stderr)
    stderr = File.read(@stderr)
    unless stderr.empty?
      rm_r @stderr # cleanup for another sub-process in the same spec
      raise SpecExpectationNotMetError, "Expected STDERR to be empty but was:\n#{stderr}"
    end

    status.should.success?
  end

  def check_status_or_print(stdout_and_stderr)
    status = $?
    unless status.success?
      raise SpecExpectationNotMetError, "Process exited with #{$?.inspect}. STDOUT + STDERR was:\n#{stdout_and_stderr}"
    end
    status.should.success?
  end

  it "is in the bindir" do
    File.dirname(RbConfig.ruby).should == @bindir
  end

  it "all launchers are in @launchers" do
    known = @launchers.keys.map(&:to_s).sort
    actual = Dir.children(@bindir).sort
    actual.delete('truffleruby-polyglot-get')
    actual.should == known
  end

  @launchers.each do |launcher, test|
    unless [:ruby, :truffleruby].include?(launcher)
      it "runs #{launcher} as an -S command" do
        redirect = launcher == :erb ? (touch @stderr; '2>&1') : @redirect
        out = ruby_exe(nil, options: "-S #{launcher} --version", args: redirect)
        check_status_and_empty_stderr
        out.should =~ test
      end
    end
  end

  @launchers.each do |launcher, test|
    it "supports running #{launcher} in bin/" do
      redirect = launcher == :erb ? (touch @stderr; '2>&1') : @redirect
      out = `#{@bindir}/#{launcher} --version #{redirect}`
      check_status_and_empty_stderr
      out.should =~ test
    end
  end

  @launchers.each do |launcher, test|
    it "supports running #{launcher} symlinked" do
      redirect = launcher == :erb ? (touch @stderr; '2>&1') : @redirect
      require 'tmpdir'
      # Use the system tmp dir to not be under the Ruby home dir
      Dir.mktmpdir do |path|
        Dir.chdir(path) do
          linkname = "linkto#{launcher}"
          File.symlink("#{@bindir}/#{launcher}", linkname)
          out = `./#{linkname} --version #{redirect}`
          check_status_and_empty_stderr
          out.should =~ test
        end
      end
    end
  end

  it "for gem can install and uninstall the hello-world gem" do
    # install
    Dir.chdir(__dir__ + '/fixtures/hello-world') do
      `"#{@bindir}/gem" build hello-world.gemspec #{@redirect}`
      check_status_and_empty_stderr
      `"#{@bindir}/gem" install --local hello-world-0.0.1.gem #{@redirect}`
      check_status_and_empty_stderr
    end

    begin
      # check that hello-world launcher is created and works
      path = "#{@bindir}/hello-world.rb"
      shebang = File.binread(path).lines.first.chomp
      if shebang.size > 127
        skip "shebang of #{path} is too long and might fail in execve(): #{shebang.size}\n#{shebang}"
      end
      out = `#{path} 2>&1`
      out.should == "Hello world! from #{RUBY_DESCRIPTION}\n"
    ensure
      # uninstall
      `#{@bindir}/gem uninstall hello-world -x #{@redirect}`
      check_status_and_empty_stderr
      File.should_not.exist?(@bindir + '/hello-world.rb')

      # TODO: remove this logic after upgrading RubyGems/bundler where the issue is fixed
      # See https://github.com/rubygems/rubygems/issues/7997
      # remove a shim file
      shim_file_path = @bindir + '/hello-world.rb.lock'
      if File.exist?(shim_file_path)
        File.unlink(shim_file_path)
      end
    end
  end

  it "for gem shows that bundled gems are installed" do
    gem_list = `#{@bindir}/gem list #{@redirect}`
    check_status_and_empty_stderr
    # see doc/contributor/stdlib.md
    @bundled_gems.each_pair do |gem, version|
      next if gem == 'typeprof' # not included on TruffleRuby
      gem_list.should =~ /#{Regexp.escape gem}.*#{Regexp.escape version}/
    end
  end

  def should_print_full_java_command(options, env: {})
    out = ruby_exe(nil, options: options, env: env, args: @redirect)
    check_status_and_empty_stderr
    parts = out.split(' ')
    parts[0].should == "$"
    parts[1].should =~ /(java|graalvm)$/
  end

  it "does not create context on --version and -v" do
    v = ruby_exe(nil, options: "--log.level=FINE -v", args: "2>&1")
    v.should_not include("createContext()")
    v.should_not include("patchContext()")
    v.should include("truffleruby ")

    version = ruby_exe(nil, options: "--log.level=FINE --version", args: "2>&1")
    version.should_not include("createContext()")
    version.should_not include("patchContext()")
    version.should include("truffleruby ")
  end

  it "preserve spaces in options" do
    out = ruby_exe("print Truffle::System.get_java_property('foo')",
                   options: '--vm.Dfoo="value with spaces"', args: @redirect)
    check_status_and_empty_stderr
    out.should == "value with spaces"
  end

  it "takes normal Ruby options from TRUFFLERUBYOPT" do
    out = ruby_exe("puts $VERBOSE", env: { "TRUFFLERUBYOPT" => "#{ENV["TRUFFLERUBYOPT"]} -W2" }, args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "takes --option options from TRUFFLERUBYOPT" do
    out = ruby_exe("puts $VERBOSE", env: { "TRUFFLERUBYOPT" => "#{ENV["TRUFFLERUBYOPT"]} --verbose=true" }, args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "takes --ruby.option options from TRUFFLERUBYOPT" do
    out = ruby_exe("puts $VERBOSE", env: { "TRUFFLERUBYOPT" => "#{ENV["TRUFFLERUBYOPT"]} --ruby.verbose=true" }, args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "takes normal Ruby options from RUBYOPT" do
    out = ruby_exe("puts $VERBOSE", env: { "RUBYOPT" => "#{ENV["RUBYOPT"]} -W2" }, args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "takes --option options from RUBYOPT" do
    out = ruby_exe("puts $VERBOSE", env: { "RUBYOPT" => "#{ENV["RUBYOPT"]} --verbose=true" }, args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "takes --ruby.option options from RUBYOPT" do
    out = ruby_exe("puts $VERBOSE", env: { "RUBYOPT" => "#{ENV["RUBYOPT"]} --ruby.verbose=true" }, args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "takes options from system properties set on the command line using --vm" do
    out = ruby_exe("puts $VERBOSE", options: "--vm.Dpolyglot.ruby.verbose=true", args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  it "prioritises options on the command line over system properties" do
    out = ruby_exe("puts $VERBOSE", options: '--vm.Dpolyglot.ruby.verbose=nil -W2', args: @redirect)
    check_status_and_empty_stderr
    out.should == "true\n"
  end

  guard -> { defined?(::TruffleRuby) && !TruffleRuby.native? } do
    it "'--vm.cp=' or '--vm.classpath=' add the jar" do
      out = ruby_exe("puts Truffle::System.get_java_property('java.class.path')", options: "--vm.cp=does-not-exist.jar", args: @redirect)
      check_status_and_empty_stderr
      out.lines[0].should include("does-not-exist.jar")

      out = ruby_exe("puts Truffle::System.get_java_property('java.class.path')", options: "--vm.classpath=does-not-exist.jar", args: @redirect)
      check_status_and_empty_stderr
      out.lines[0].should include("does-not-exist.jar")
    end
  end

  it "prints available user options for --help:languages" do
    out = ruby_exe(nil, options: "--help:languages", args: @redirect)
    check_status_and_empty_stderr
    out.should include("--ruby.verbose")
  end

  it "prints available expert options for --help:languages --help:expert" do
    out = ruby_exe(nil, options: "--help:languages --help:expert", args: @redirect)
    check_status_and_empty_stderr
    out.should include("--ruby.cexts-log-load")
  end

  it "prints available internal options for --help:languages --help:internal" do
    out = ruby_exe(nil, options: "--help:languages --help:internal", args: @redirect)
    check_status_and_empty_stderr
    out.should include("--ruby.default-cache")
  end

  it "logs options if --options-log is set" do
    out = ruby_exe("14", options: "--experimental-options --log.level=CONFIG --options-log", args: "2>&1")
    check_status_or_print(out)
    out.should include("[ruby] CONFIG")
  end

  it "prints an error for an unknown option" do
    out = ruby_exe(nil, options: "--unknown=value", args: "2>&1", exit_status: 2)
    $?.success?.should == false
    out.should include("invalid option --unknown=value")

    out = ruby_exe(nil, options: "--ruby.unknown=value", args: "2>&1", exit_status: 2)
    $?.success?.should == false
    out.should include("invalid option --ruby.unknown=value")
  end

  it "sets the log level using --log.level=" do
    out = ruby_exe("14", options: "--experimental-options --options-log --log.level=CONFIG", args: "2>&1")
    check_status_or_print(out)
    out.should include("CONFIG: option default-cache=")
  end

  it "sets the log level using --log.ruby.level=" do
    out = ruby_exe("14", options: "--experimental-options --options-log --log.ruby.level=CONFIG", args: "2>&1")
    check_status_or_print(out)
    out.should include("CONFIG: option default-cache=")
  end

  describe 'StringArray option' do
    it "appends multiple options" do
      out = ruby_exe("puts $LOAD_PATH", options: "-I a -I b", args: @redirect)
      check_status_and_empty_stderr
      out.lines[0].should == "#{Dir.pwd}/a\n"
      out.lines[1].should == "#{Dir.pwd}/b\n"
    end

    it "parses ," do
      out = ruby_exe("puts $LOAD_PATH", options: "--load-paths=a,b", args: @redirect)
      check_status_and_empty_stderr
      out.lines[0].should == "#{Dir.pwd}/a\n"
      out.lines[1].should == "#{Dir.pwd}/b\n"
    end

    it "parses , respecting escaping" do
      # \\\\ translates to one \
      out = ruby_exe("puts $LOAD_PATH", options: "--load-paths=a\\\\,b,,\\\\c", args: @redirect)
      check_status_and_empty_stderr
      out.lines[0].should == "#{Dir.pwd}/a,b\n"
      out.lines[1].should == "#{Dir.pwd}\n"
      out.lines[2].should == "#{Dir.pwd}/\\c\n"
    end
  end

  it "enables deterministic hashing if --hashing-deterministic is set" do
    out = ruby_exe("puts 14.hash", options: "--experimental-options --hashing-deterministic", args: "2>&1")
    check_status_or_print(out)
    out.should include("SEVERE: deterministic hashing is enabled - this may make you vulnerable to denial of service attacks")
    out.should include("3412061130696242594")
  end

  it "prints help containing runtime options" do
    out = ruby_exe(nil, options: "--help", args: @redirect)
    check_status_and_empty_stderr

    if TruffleRuby.native?
      out.should include("--native")
    else
      out.should include("--jvm")
    end
  end

  it "prints help:ruby containing ruby language options" do
    out = ruby_exe(nil, options: "--help:ruby", args: @redirect)
    check_status_and_empty_stderr
    out.should =~ /Language options/i
    out.should =~ /\bRuby\b/
    out.should.include?('https://github.com/truffleruby/truffleruby')
    out.should.include?("--ruby.load-paths=")
  end

  it "prints the version with --version" do
    out = ruby_exe(nil, options: "--version", args: @redirect)
    check_status_and_empty_stderr
    out.should include(RUBY_DESCRIPTION)
  end

  it "understands ruby polyglot options" do
    out = ruby_exe("p Truffle::Boot.get_option('rubygems')", options: "--experimental-options --ruby.rubygems=false", args: @redirect)
    check_status_and_empty_stderr
    out.should include('false')
  end

  it "understands ruby polyglot options without ruby. prefix" do
    out = ruby_exe("p Truffle::Boot.get_option('rubygems')", options: "--experimental-options --rubygems=false", args: @redirect)
    check_status_and_empty_stderr
    out.should include('false')
  end

  it "does not print a Java backtrace for an -S file that's not found" do
    out = ruby_exe(nil, options: "-S does_not_exist", args: "2>&1", exit_status: 1)
    $?.success?.should == false
    out.should include('truffleruby: No such file or directory -- does_not_exist (LoadError)')
    out.should_not include('boot.rb')
    out.should_not include('RubyLauncher.main')
  end

  it "ignores --jit... options with a warning and a hint to look at Graal documentation" do
    jit_options = %w[--jit --jit-warnings --jit-debug --jit-wait --jit-save-temps --jit-verbose --jit-max-cache --jit-min-calls]
    out = ruby_exe("p 14", options: jit_options.join(' '), args: "2>&1")
    check_status_or_print(out)
    out.should include("JIT options are not supported - see the Graal documentation instead")
    out.should include("14")
  end

  it "warns on ignored options" do
    [
      "-y",
      "--yydebug",
      "--debug-frozen-string-literal",
      "--dump=insns",
    ].each do |option|
      out = ruby_exe("p 14", options: option, args: "2>&1")
      check_status_or_print(out)
      out.should include("[ruby] WARNING the #{option} switch is silently ignored as it is an internal development tool")
      out.should include("14")
    end
  end

  it "warns if the locale is not set properly" do
    err = ruby_exe("Encoding.find('locale')", args: "2>&1", env: { "LC_ALL" => "C" })
    err.should.include? "[ruby] WARNING: Encoding.find('locale') is US-ASCII (due to nl_langinfo(CODESET) which returned "
    err.should.include? "), this often indicates that the system locale is not set properly"
  end

  ['RUBYOPT', 'TRUFFLERUBYOPT'].each do |var|
    it "should recognize ruby --vm options in #{var}" do
      out = ruby_exe('print Truffle::System.get_java_property("foo")', env: { var => "#{ENV[var]} --vm.Dfoo=bar" }, args: @redirect)
      check_status_and_empty_stderr
      out.should == 'bar'
    end
  end
end
