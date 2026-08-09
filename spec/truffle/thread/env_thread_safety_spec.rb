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

describe "ENV thread safety" do

  it "supports concurrent access from multiple threads" do
    n_threads = 8
    n = 200
    keys = Array.new(10) { |i| "TRUFFLERUBY_ENV_THREAD_SAFETY_#{i}" }

    # This reproduces the pattern of Bundler.with_unbundled_env called
    # concurrently: threads repeatedly mutate the environment and replace it
    # with a snapshot, which used to corrupt ENV's internal state and crash
    # with an internal Null receiver error.
    start = Queue.new
    go = Queue.new
    threads = Array.new(n_threads) do |t|
      Thread.new do
        start << true
        go.pop
        n.times do |i|
          key = keys[(i + t) % keys.size]
          ENV[key] = "value"
          ENV[key]
          ENV.each { nil }
          ENV.replace(ENV.to_hash)
          ENV.delete(key) if i % 3 == 0
        end
      end
    end
    n_threads.times { start.pop }
    n_threads.times { go << true }
    threads.each(&:join)

    # The internal state of ENV must remain consistent: every key it tracks
    # must still be readable from the process environment.
    ENV.size.should == ENV.keys.size
    ENV.keys.each do |key|
      ENV[key].should_not == nil
    end
  ensure
    keys.each { |key| ENV.delete(key) }
  end

end
