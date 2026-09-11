require_relative '../spec_helper'

platform_is_not :windows do
  [Socket, UNIXSocket].each do |socket_class|
    methods = [:recv, :recv_nonblock, :recvmsg, :recvmsg_nonblock, :recvfrom]
    methods << :recvfrom_nonblock if socket_class == Socket

    methods.each do |method|
      describe "#{socket_class}##{method} with buffered IO" do
        before :each do
          @receiver, @sender = socket_class == Socket ? Socket.pair(:UNIX, :STREAM, 0) : UNIXSocket.pair
        end

        after :each do
          @receiver.close
          @sender.close
        end

        it "raises IOError without consuming buffered or socket data" do
          @receiver.ungetc("x")
          # Supply native data so a missing buffer check fails without blocking.
          @sender.write("y")

          -> { @receiver.public_send(method, 1) }.should.raise(IOError)
          @receiver.read(2).should == "xy"
        end
      end
    end
  end
end
