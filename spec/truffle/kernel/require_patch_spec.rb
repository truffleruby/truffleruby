# Copyright (c) 2026 TruffleRuby contributors
#
# Redistribution and use in source and binary forms, with or without
# modification, are permitted provided that the following conditions are met:
#
# 1. Redistributions of source code must retain the above copyright notice, this
#    list of conditions and the following disclaimer.
#
# 2. Redistributions in binary form must reproduce the above copyright notice,
#    this list of conditions and the following disclaimer in the documentation
#    and/or other materials provided with the distribution.
#
# 3. Neither the name of the copyright holder nor the names of its
#    contributors may be used to endorse or promote products derived from
#    this software without specific prior written permission.
#
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
# AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
# IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
# DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
# FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
# DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
# SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
# CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
# OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

require_relative '../../ruby/spec_helper'

# Each example runs in a new process, since whether a patch has already run is global state.
describe "Kernel#require of a feature with a patch" do
  before :each do
    @dir = tmp("require_patch")
    mkdir_p "#{@dir}/racc"
    @dir = File.realpath(@dir)
    @dlext = RbConfig::CONFIG['DLEXT']

    # None of these are real C extensions, so loading one raises. That's how we tell whether the real file was loaded
    # instead of the patch.
    touch("#{@dir}/racc/cparse.#{@dlext}") { |f| f.write "not a C extension" }
    touch("#{@dir}/digest.#{@dlext}") { |f| f.write "not a C extension" }

    # A stand-in for the thread_safe gem's thread_safe.rb. TruffleRuby's patch for it (lib/patches/thread_safe.rb)
    # requires this file before making its own changes. It raises until $thread_safe_retry is set, so we can check that
    # the patch runs again after the file it wraps fails to load.
    touch("#{@dir}/thread_safe.rb") { |f| f.write "raise 'original failed' unless $thread_safe_retry\n" }
  end

  after :each do
    rm_r @dir
  end

  def require_and_report(code)
    ruby_exe(<<~RUBY, options: "-I#{@dir}")
      #{code}
      begin
        p require(FEATURE)
      rescue Exception => e
        puts "\#{e.class}: \#{e.message}"
      end
    RUBY
  end

  it "raises a LoadError for racc/cparse so racc uses its pure-Ruby runtime" do
    out = ruby_exe(<<~RUBY, options: "-I#{@dir}")
      begin
        require 'racc/cparse'
      rescue LoadError => e
        puts e.message, e.path
      end
    RUBY
    out.should == "TruffleRuby uses the pure-Ruby racc runtime as it is faster than racc/cparse\nracc/cparse\n"
  end

  it "applies the patch to an absolute path, as Bootsnap requires" do
    out = require_and_report("FEATURE = '#{@dir}/racc/cparse.#{@dlext}'")
    out.should.include?("TruffleRuby uses the pure-Ruby racc runtime")
  end

  it "applies the patch to an absolute path through a symlink" do
    link = "#{@dir}-link"
    File.symlink(@dir, link)
    begin
      out = ruby_exe(<<~RUBY, options: "-I#{link}")
        begin
          require '#{link}/racc/cparse.#{@dlext}'
        rescue LoadError => e
          puts e.message
        end
      RUBY
      out.should.include?("TruffleRuby uses the pure-Ruby racc runtime")
    ensure
      rm_r link
    end
  end

  it "applies the patch to a feature with the .so extension" do
    out = require_and_report("FEATURE = 'racc/cparse.so'")
    out.should.include?("TruffleRuby uses the pure-Ruby racc runtime")
  end

  it "loads racc/cparse if a falsy top-level Racc_No_Extensions is defined" do
    out = require_and_report("FEATURE = 'racc/cparse'; Racc_No_Extensions = nil")
    out.should.start_with?("LoadError: ")
    out.should.include?("#{@dir}/racc/cparse.#{@dlext}")
  end

  it "applies the patch again after loading racc/cparse failed" do
    out = require_and_report(<<~RUBY)
      FEATURE = 'racc/cparse'
      Racc_No_Extensions = false
      begin
        require FEATURE
      rescue Exception
      end
      Object.send(:remove_const, :Racc_No_Extensions)
    RUBY
    out.should.include?("TruffleRuby uses the pure-Ruby racc runtime")
  end

  it "applies a patch again after its original failed to load" do
    out = ruby_exe(<<~RUBY, options: "-I#{@dir}")
      begin
        require 'thread_safe'
      rescue => e
        puts e.message
      end
      $thread_safe_retry = true
      p require('thread_safe')
      p defined?(ThreadSafe::Hash)
    RUBY
    out.should == "original failed\ntrue\n\"constant\"\n"
  end

  it "does not apply a patch for a Ruby file to a C extension with the same name" do
    # The digest patch replaces digest.rb. Requiring 'digest' resolves to digest.rb, not to this C extension.
    out = require_and_report("FEATURE = '#{@dir}/digest.#{@dlext}'")
    out.should.start_with?("LoadError: ")
    out.should.include?("#{@dir}/digest.#{@dlext}")
  end
end
