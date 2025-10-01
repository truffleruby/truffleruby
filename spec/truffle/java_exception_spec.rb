# Copyright (c) 2017, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

describe "Internal errors reaching the top level" do
  it "show both the Java stacktrace and Ruby backtrace" do
    file = fixture(__FILE__, 'throw_java_exception.rb')
    out = ruby_exe(file, args: "2>&1", exit_status: 1)
    out = out.gsub(/\.java:\d+/, '.java:LINE')
    out.should == <<-EOS

truffleruby: an internal exception escaped out of the interpreter,
please report it to https://github.com/truffleruby/truffleruby/issues

```
custom message (java.lang.RuntimeException)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionNode.throwingMethod(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionNode.callingMethod(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionNode.throwJavaException(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodesFactory$ThrowJavaExceptionNodeFactory$ThrowJavaExceptionNodeGen.execute(TruffleDebugNodesFactory.java:LINE)
	from org.truffleruby.language.RubyCoreMethodRootNode.execute(RubyCoreMethodRootNode.java:LINE)
#{file}:10:in `throw_java_exception'
	from #{file}:10:in `foo'
	from #{file}:13:in `<main>'
```
    EOS
  end

  it "are also reported if they happen in at_exit" do
    file = fixture(__FILE__, 'throw_java_exception_at_exit.rb')
    out = ruby_exe(file, args: "2>&1", exit_status: 1)
    out = out.gsub(/\.java:\d+/, '.java:LINE')
    out.should == <<-EOS

truffleruby: unexpected internal exception in at_exit,
please report it to https://github.com/truffleruby/truffleruby/issues

```
custom message (java.lang.RuntimeException)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionNode.throwingMethod(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionNode.callingMethod(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionNode.throwJavaException(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodesFactory$ThrowJavaExceptionNodeFactory$ThrowJavaExceptionNodeGen.execute(TruffleDebugNodesFactory.java:LINE)
	from org.truffleruby.language.RubyCoreMethodRootNode.execute(RubyCoreMethodRootNode.java:LINE)
#{file}:10:in `throw_java_exception'
	from #{file}:10:in `foo'
	from #{file}:14:in `block in <main>'
```
    EOS
  end

  it "show the cause" do
    file = fixture(__FILE__, 'throw_java_exception_with_cause.rb')
    out = ruby_exe(file, args: "2>&1", exit_status: 1)
    out = out.gsub(/\.java:\d+/, '.java:LINE')
    out.should == <<-EOS

truffleruby: an internal exception escaped out of the interpreter,
please report it to https://github.com/truffleruby/truffleruby/issues

```
message (java.lang.RuntimeException)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionWithCauseNode.throwJavaExceptionWithCause(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodesFactory$ThrowJavaExceptionWithCauseNodeFactory$ThrowJavaExceptionWithCauseNodeGen.execute(TruffleDebugNodesFactory.java:LINE)
	from org.truffleruby.language.RubyCoreMethodRootNode.execute(RubyCoreMethodRootNode.java:LINE)
#{file}:10:in `throw_java_exception_with_cause'
	from #{file}:10:in `foo'
	from #{file}:13:in `<main>'
Caused by:
cause 1 (java.lang.RuntimeException)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionWithCauseNode.throwJavaExceptionWithCause(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodesFactory$ThrowJavaExceptionWithCauseNodeFactory$ThrowJavaExceptionWithCauseNodeGen.execute(TruffleDebugNodesFactory.java:LINE)
	from org.truffleruby.language.RubyCoreMethodRootNode.execute(RubyCoreMethodRootNode.java:LINE)
#{file}:10:in `throw_java_exception_with_cause'
	from #{file}:10:in `foo'
	from #{file}:13:in `<main>'
Caused by:
cause 2 (java.lang.RuntimeException)
	from org.truffleruby.debug.TruffleDebugNodes$ThrowJavaExceptionWithCauseNode.throwJavaExceptionWithCause(TruffleDebugNodes.java:LINE)
	from org.truffleruby.debug.TruffleDebugNodesFactory$ThrowJavaExceptionWithCauseNodeFactory$ThrowJavaExceptionWithCauseNodeGen.execute(TruffleDebugNodesFactory.java:LINE)
	from org.truffleruby.language.RubyCoreMethodRootNode.execute(RubyCoreMethodRootNode.java:LINE)
#{file}:10:in `throw_java_exception_with_cause'
	from #{file}:10:in `foo'
	from #{file}:13:in `<main>'
```
    EOS
  end

  it "AssertionError in another Thread is rethrown on the main Ruby Thread" do
    code = <<-RUBY
    Thread.new do
      Truffle::Debug.throw_assertion_error('custom_assertion_error_message')
    end
    sleep
    RUBY
    out = ruby_exe(code, args: "2>&1", exit_status: 1)
    $?.exitstatus.should == 1
    out.should.include? "```\nRuby Thread"
    out.should.include? "terminated with internal error: (java.lang.RuntimeException)"
    out.should.include? "Caused by:\ncustom_assertion_error_message (java.lang.AssertionError)"
  end
end
