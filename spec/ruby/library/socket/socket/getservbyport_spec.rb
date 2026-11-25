require_relative '../spec_helper'

describe 'Socket.getservbyport' do
  platform_is_not :windows do
    it 'returns the service name as a String' do
      Socket.getservbyport(514).should == 'shell'
    end
  end

  platform_is :windows do
    it 'returns the service name as a String' do
      Socket.getservbyport(514).should == 'cmd'
    end
  end

  it 'returns the service name when using a custom protocol name' do
    Socket.getservbyport(514, 'udp').should == 'syslog'
  end

  it 'converts the port with #to_int and the protocol name with #to_str' do
    port = mock('port')
    protocol = mock('protocol')
    port.should_receive(:to_int).and_return(514)
    protocol.should_receive(:to_str).and_return('udp')

    Socket.getservbyport(port, protocol).should == 'syslog'
  end

  it 'raises SocketError for an unknown port number' do
    -> { Socket.getservbyport(0) }.should.raise(SocketError)
  end
end
