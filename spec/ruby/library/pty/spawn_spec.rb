require_relative '../../spec_helper'
require_relative '../../core/process/fixtures/common'

platform_is_not :windows do
  require 'pty'

  describe "PTY.spawn" do
    def read_all(r)
      output = +""
      begin
        while line = r.gets
          output << line
        end
      rescue Errno::EIO
        # raised on Linux when the slave is closed, i.e. when the child exited
      end
      output
    end

    def spawn_and_read(*args)
      r, w, pid = PTY.spawn(*args)
      begin
        read_all(r)
      ensure
        r.close
        w.close
        Process.wait(pid)
      end
    end

    it "returns [r, w, pid] with the pty connected to stdin, stdout and stderr of the command" do
      r, w, pid = PTY.spawn("sh", "-c", "read line; echo out $line; echo err $line >&2")
      begin
        pid.should.is_a?(Integer)
        w.sync.should == true
        r.should.close_on_exec?
        w.should.close_on_exec?
        w.puts "foo"
        lines = read_all(r).lines
        lines.should.include?("out foo\r\n")
        lines.should.include?("err foo\r\n")
      ensure
        r.close
        w.close
        Process.wait(pid)
      end
      $?.should.success?
    end

    it "yields [r, w, pid] and returns nil when given a block" do
      PTY.spawn("echo", "foo") do |r, w, pid|
        begin
          r.gets.should == "foo\r\n"
        ensure
          r.close
          w.close
          Process.wait(pid)
        end
      end.should == nil
    end

    it "makes the pty the stdin, stdout and stderr of the command" do
      r, w, pid = PTY.spawn("sh", "-c", "test -t 0 && test -t 1 && test -t 2 && tty")
      begin
        read_all(r).should == "#{r.path}\r\n"
      ensure
        r.close
        w.close
        Process.wait(pid)
      end
    end

    it "makes the pty the controlling terminal of the command" do
      # Opening /dev/tty only succeeds if the process has a controlling terminal
      spawn_and_read("sh", "-c", ": < /dev/tty && echo ok").should == "ok\r\n"
    end

    it "makes the command the leader of a new session" do
      r, w, pid = PTY.spawn("sh", "-c", "echo ready; cat")
      begin
        r.gets.should == "ready\r\n"
        Process.getsid(pid).should == pid
        Process.getpgid(pid).should == pid
      ensure
        r.close
        w.close
        Process.wait(pid)
      end
    end

    it "accepts a command line" do
      spawn_and_read("echo foo; echo bar").should == "foo\r\nbar\r\n"
    end

    it "accepts an env Hash" do
      spawn_and_read({ "PTY_SPEC_VAR" => "value" }, "echo $PTY_SPEC_VAR").should == "value\r\n"
    end

    guard_not -> { ProcessSpecs.busybox_sh? } do
      it "accepts [command, argv0]" do
        spawn_and_read(["sh", "argv0"], "-c", "echo $0").should == "argv0\r\n"
      end
    end

    it "gives precedence to the redirections in options over the pty" do
      spawn_and_read("sh", "-c", "echo to-stderr >&2; echo to-stdout", err: File::NULL).should == "to-stdout\r\n"
    end

    it "makes the pty the controlling terminal of the command even if stdin is redirected" do
      spawn_and_read("sh", "-c", ": < /dev/tty && echo ok", in: File::NULL).should == "ok\r\n"
    end

    it "raises Errno::EPERM for the pgroup option as the command is a session leader" do
      -> { PTY.spawn("sh", "-c", "echo ran", pgroup: true) }.should.raise(Errno::EPERM)
    end

    it "is a module function" do
      PTY.private_instance_methods(false).should.include?(:spawn)
      PTY.private_instance_methods(false).should.include?(:getpty)
    end

    it "raises Errno::ENOENT if the command does not exist" do
      -> { PTY.spawn(tmp("pty_spawn_nonexistent")) }.should.raise(Errno::ENOENT)
    end

    it "is also available as PTY.getpty" do
      PTY.method(:getpty).should == PTY.method(:spawn)
    end
  end
end
