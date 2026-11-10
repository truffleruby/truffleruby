require_relative '../../spec_helper'

platform_is_not :windows do
  require 'pty'

  describe "PTY.check" do
    before :each do
      @r, @w, @pid = PTY.spawn("cat")
    end

    after :each do
      @r.close unless @r.closed?
      @w.close unless @w.closed?
      begin
        Process.wait(@pid)
      rescue Errno::ECHILD
        # already reaped by PTY.check
      end
    end

    def exit_child
      # Closing the master sends SIGHUP to the child
      @w.close
      @r.close
    end

    def wait_for_status
      status = nil
      sleep 0.01 until status = yield
      status
    end

    it "returns nil if the process is still running" do
      PTY.check(@pid).should == nil
      PTY.check(@pid, true).should == nil
    end

    it "returns the Process::Status if the process exited" do
      exit_child
      status = wait_for_status { PTY.check(@pid) }
      status.should.is_a?(Process::Status)
      status.pid.should == @pid
    end

    it "raises PTY::ChildExited with the status if the process exited and raise is true" do
      exit_child
      begin
        wait_for_status { PTY.check(@pid, true) }
      rescue PTY::ChildExited => e
        e.message.should == "pty - exited: #{@pid}"
        e.status.should.is_a?(Process::Status)
        e.status.pid.should == @pid
      else
        raise "PTY::ChildExited was not raised"
      end
    end
  end

  describe "PTY::ChildExited" do
    it "is a subclass of RuntimeError" do
      PTY::ChildExited.superclass.should == RuntimeError
    end
  end
end
