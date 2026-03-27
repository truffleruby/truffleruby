# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.
# OTHER DEALINGS IN THE SOFTWARE.

require_relative '../ruby/spec_helper'

guard -> { TruffleRuby.native? } do
  describe "The pre-initialized context" do
    guard -> { ENV["TRUFFLERUBY_CHECK_PREINITIALIZED_SPEC"] != "false" } do
      it "can be used to run specs, as otherwise this spec run will not have tested pre-initialization" do
        Truffle::Boot.was_preinitialized?.should == true
      end
    end

    it "is not used if --engine.UsePreInitializedContext=false is passed" do
      out = ruby_exe("p Truffle::Boot.was_preinitialized?", options: "--experimental-options --engine.UsePreInitializedContext=false")
      out.should == "false\n"
    end

    it "is used when passing no incompatible options" do
      out = ruby_exe("p Truffle::Boot.was_preinitialized?", options: "--log.level=FINE", args: "2>&1")
      out.should include("patchContext()")
      out.should_not include("createContext()")
      out.should_not include("initializeContext()")
      out.should include("\ntrue\n")
    end

    it "is not used when passing incompatible options" do
      no_native = "--experimental-options --patching=false --disable-gems"
      out = ruby_exe("p Truffle::Boot.was_preinitialized?", options: "--log.level=FINE #{no_native}", args: "2>&1")
      out.should include("patchContext()")
      out.should include("not reusing pre-initialized context: loading patching is false")
      out.should include("finalizeContext()")
      out.should include("disposeContext()")
      out.should include("createContext()")
      out.should include("initializeContext()")
      out.should include("\nfalse\n")
    end

    it "is used when $VERBOSE changes" do
      code = "p [$VERBOSE, Truffle::Boot.was_preinitialized?]"
      # -w/-W in RUBYOPT overrides -w on the command-line, like in MRI,
      # so unset the RUBYOPT and TRUFFLERUBYOPT env vars
      env = { "RUBYOPT" => nil, "TRUFFLERUBYOPT" => nil }
      ruby_exe(code, env: env).should == "[false, true]\n"
      ruby_exe(code, options: "-w", env: env).should == "[true, true]\n"
      ruby_exe(code, options: "-W0", env: env).should == "[nil, true]\n"
      ruby_exe(code, options: "-v", env: env).should include "[true, true]\n"
    end

    it "is used with --disable-gems so startup with --disable-gems is not slower" do
      code = "p [defined?(Gem), Truffle::Boot.was_preinitialized?]"
      ruby_exe(code, options: "--disable-gems").should == "[nil, true]\n"
    end

    it "picks up new environment variables" do
      var = "TR_PRE_INIT_NEW_VAR"
      ENV[var] = "true"
      begin
        ruby_exe("print ENV['#{var}']").should == "true"
      ensure
        ENV.delete var
      end
    end

    it "uses the runtime value of $TZ" do
      with_timezone("America/New_York") do
        Time.local(2001, 1, 1).zone.should == "EST"
        env = { "TZ" => "Pacific/Honolulu" }
        ruby_exe("print Time.local(2001, 1, 1).zone", env: env).should == "HST"
      end
    end
  end
end
