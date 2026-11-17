require_relative '../../spec_helper'

platform_is_not :windows do
  require 'pty'

  describe "PTY.open" do
    it "returns the master IO and the slave File of a new pty" do
      master, slave = PTY.open
      begin
        master.should.instance_of?(IO)
        slave.should.instance_of?(File)
        slave.should.tty?
        File.chardev?(slave.path).should == true
        master.path.should == "masterpty:#{slave.path}"
      ensure
        master.close
        slave.close
      end
    end

    it "returns IOs which are sync and close-on-exec" do
      PTY.open do |master, slave|
        master.sync.should == true
        slave.sync.should == true
        master.should.close_on_exec?
        slave.should.close_on_exec?
      end
    end

    it "connects the master and the slave" do
      PTY.open do |master, slave|
        slave.puts "foo"
        master.gets.chomp.should == "foo"
        master.puts "bar"
        slave.gets.chomp.should == "bar"
      end
    end

    it "yields the master and slave, closes them after the block and returns the value of the block" do
      ios = nil
      PTY.open do |master, slave|
        ios = [master, slave]
        :result
      end.should == :result
      ios[0].should.closed?
      ios[1].should.closed?
    end

    it "does not raise if the master and slave are closed in the block" do
      PTY.open do |master, slave|
        master.close
        slave.close
        :result
      end.should == :result
    end
  end
end
