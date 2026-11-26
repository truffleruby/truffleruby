require_relative '../../spec_helper'

describe "Thread" do
  it "defines Mutex" do
    Thread.const_defined?(:Mutex, false).should == true
    Thread::Mutex.should.equal?(Mutex)
  end

  it "defines ConditionVariable" do
    Thread.const_defined?(:ConditionVariable, false).should == true
    Thread::ConditionVariable.should.equal?(ConditionVariable)
  end

  it "defines Queue" do
    Thread.const_defined?(:Queue, false).should == true
    Thread::Queue.should.equal?(Queue)
  end

  it "defines SizedQueue" do
    Thread.const_defined?(:SizedQueue, false).should == true
    Thread::SizedQueue.should.equal?(SizedQueue)
  end

  it "does not define ThreadGroup" do
    Thread.const_defined?(:ThreadGroup, false).should == false
  end
end
