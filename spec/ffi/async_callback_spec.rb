#
# This file is part of ruby-ffi.
# For licensing, see LICENSE.SPECS
#

require File.expand_path(File.join(File.dirname(__FILE__), "spec_helper"))

module AsyncCallbackSpec
describe "async callback" do
  module LibTest
    extend FFI::Library
    ffi_lib TestLibrary::PATH
    AsyncIntCallback = callback [ :int ], :void

    @blocking = true
    attach_function :testAsyncCallback, [ AsyncIntCallback, :int ], :void
    @blocking = true
    attach_function :testAsyncCallbackDelayedRegister, [ AsyncIntCallback ], :void
    @blocking = true
    attach_function :testAsyncCallbackDelayedTrigger, [ :int ], :void
    freeze
  end

  it ":int (0x7fffffff) argument" do
    skip "not yet supported on TruffleRuby" if RUBY_ENGINE == "truffleruby"
    v = 0xdeadbeef
    called = false
    cb = Proc.new {|i| v = i; called = Thread.current }
    LibTest.testAsyncCallback(cb, 0x7fffffff)
    expect(called).to be_kind_of(Thread)
    expect(called).to_not eq(Thread.current)
    expect(v).to eq(0x7fffffff)
  end

  it "called a second time" do
    skip "not yet supported on TruffleRuby" if RUBY_ENGINE == "truffleruby"
    v = 1
    th1 = th2 = false
    LibTest.testAsyncCallback(2) { |i| v += i; th1 = Thread.current }
    LibTest.testAsyncCallback(3) { |i| v += i; th2 = Thread.current }
    expect(th1).to be_kind_of(Thread)
    expect(th2).to be_kind_of(Thread)
    expect(th1).to_not eq(Thread.current)
    expect(th2).to_not eq(Thread.current)
    expect(th1).to_not eq(th2)
    expect(v).to eq(6)
  end

  it "sets the name of the thread that runs the callback" do
    skip "not yet supported on TruffleRuby" if RUBY_ENGINE == "truffleruby"
    skip "not yet supported on JRuby" if RUBY_ENGINE == "jruby"

    callback_runner_thread = nil

    LibTest.testAsyncCallback(proc { callback_runner_thread = Thread.current }, 0)

    expect(callback_runner_thread.name).to eq("FFI Callback Runner")
  end

  it "works in Ractor", :ractor do
    skip "not yet supported on TruffleRuby" if RUBY_ENGINE == "truffleruby"

    res = Ractor.new do
      v = 0xdeadbeef
      correct_ractor = false
      correct_thread = false
      thread = Thread.current
      rac = Ractor.current
      cb = Proc.new do |i|
        v = i
        correct_ractor = rac == Ractor.current
        correct_thread = thread != Thread.current
      end
      LibTest.testAsyncCallback(cb, 0x7fffffff)

      [v, correct_ractor, correct_thread]
    end.value

    expect(res).to eq([0x7fffffff, true, true])
  end

  it "works in forks" do
    skip "no forking on this ruby" unless Process.respond_to?(:fork)

    v = 0
    LibTest.testAsyncCallbackDelayedRegister { |i| v = i }
    IO.popen('-') do |pipe|
      if pipe
        begin
          # Parent process - read the value and assert it.
          # Wait two seconds for the pipe to be readable (since the write of a four-byte int
          # is < PIPE_BUF, there should be no possibility of only _some_ of the data being ready)
          IO.select([pipe], [], [], 2)
          data = pipe.read_nonblock(4)
          expect(data).to_not be_nil
          child_v = data.unpack1('l')
          expect(child_v).to eq(125512)
        ensure
          # Make sure we don't wait forever for this test if the child process hangs.
          Process.kill :KILL, pipe.pid
        end
      else
        begin
          # Child process - call the callback and write the result
          LibTest.testAsyncCallbackDelayedTrigger(125512)
          $stdout.write [v].pack('l')
        rescue Exception => e
          $stderr.puts e.inspect
        ensure
          # Make sure control never returns to the test runner
          exit! 0
        end
      end
    end
  end
end
end
