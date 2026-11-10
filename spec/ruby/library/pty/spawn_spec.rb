require_relative '../../spec_helper'

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
        r.gets.chomp.should == r.path
      ensure
        r.close
        w.close
        Process.wait(pid)
      end
      $?.should.success?
    end

    it "makes the command the leader of a new session with the pty as controlling terminal" do
      # Opening /dev/tty only succeeds if the process has a controlling terminal
      output = spawn_and_read("sh", "-c", "ps -o pid= -o sid= -p $$; tty < /dev/tty")
      lines = output.lines.map(&:strip)
      pid, sid = lines[0].split
      pid.should == sid
      sid.should_not == Process.getsid.to_s
      lines[1].should =~ /\A\/dev\//
    end

    it "accepts a command line" do
      spawn_and_read("echo foo; echo bar").should == "foo\r\nbar\r\n"
    end

    it "accepts an env Hash" do
      spawn_and_read({ "PTY_SPEC_VAR" => "value" }, "echo $PTY_SPEC_VAR").should == "value\r\n"
    end

    it "accepts [command, argv0]" do
      spawn_and_read(["sh", "argv0"], "-c", "echo $0").should == "argv0\r\n"
    end

    it "raises Errno::ENOENT if the command does not exist" do
      -> { PTY.spawn(tmp("pty_spawn_nonexistent")) }.should.raise(Errno::ENOENT)
    end

    it "is also available as PTY.getpty" do
      PTY.method(:getpty).should == PTY.method(:spawn)
    end
  end
end
