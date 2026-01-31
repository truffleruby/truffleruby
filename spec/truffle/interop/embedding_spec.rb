# Copyright (c) 2026, TruffleRuby contributors
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

describe "Embedding via the Context API" do
  def with_context(ctx)
    yield ctx
  ensure
    ctx.close
  end

  before :all do
    @context = Java.type("org.graalvm.polyglot.Context")
    @source = Java.type("org.graalvm.polyglot.Source")
  end

  it "works" do
    if TruffleRuby.jit? # skip in interpreter to avoid warnings
      with_context(@context.create) do |ctx|
        ctx.eval("ruby", "2**3").should == 8
      end
    end

    with_context(@context.newBuilder.option("engine.WarnInterpreterOnly", "false").build) do |ctx|
      ctx.eval("ruby", "2**3").should == 8
      ctx.eval(@source.create("ruby", "2**3")).should == 8
    end
  end

  it "supports setting environment variables" do
    with_context(@context.newBuilder
      .option("engine.WarnInterpreterOnly", "false")
      .allowNativeAccess(true) # FIXME
      .environment("FOO", "bar")
      .build) do |ctx|
      ctx.eval("ruby", "ENV['FOO']").should == "bar"
    end
  end

  it "supports setting the working directory" do
    path = Java.type("java.nio.file.Path")

    with_context(@context.newBuilder
      .option("engine.WarnInterpreterOnly", "false")
      .allowAllAccess(true) # FIXME
      .currentWorkingDirectory(path.of(__dir__))
      .build) do |ctx|
      ctx.eval("ruby", "Dir.pwd").should == __dir__
    end
  end
end
