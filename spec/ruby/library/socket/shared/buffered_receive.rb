describe :socket_buffered_receive, shared: true do
  before :each do
    @receiver, @sender = @object.call
  end

  after :each do
    @receiver.close
    @sender.close
  end

  it "raises IOError without consuming buffered or socket data" do
    @receiver.ungetc("x")
    # Supply native data so a missing buffer check fails without blocking.
    @sender.write("y")

    # CRuby names the syscall it would use: recv(2) for the blocking variants, recvfrom(2) for the
    # non-blocking ones, and recvmsg(2) for both #recvmsg variants.
    syscall = case @method
              when :recvmsg, :recvmsg_nonblock then "recvmsg"
              when :recv_nonblock, :recvfrom_nonblock then "recvfrom"
              else "recv"
              end

    -> { @receiver.public_send(@method, 1) }.should.raise(IOError, "#{syscall} for buffered IO")
    @receiver.read(2).should == "xy"
  end
end
