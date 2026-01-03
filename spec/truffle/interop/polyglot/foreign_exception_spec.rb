# Copyright (c) 2022, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignException" do
  before :each do
    @foreign = Truffle::Debug.foreign_exception("exception message")
  end

  it "subclasses StandardError" do
    @foreign.class.should == Polyglot::ForeignException
    @foreign.class.superclass.should == StandardError
  end

  it "can be caught with rescue => e" do
    begin
      raise @foreign
    rescue => e
      e.should == @foreign
    end
  end

  it "supports #message" do
    @foreign.message.should == "exception message"
  end

  it "supports #cause" do
    @foreign.cause.should == nil
  end

  it "supports #full_message" do
    -> {
      raise @foreign
    }.should raise_error(Polyglot::ForeignException) { |e|
      full_message = e.full_message(highlight: false, order: :top).lines
      full_message[0].should == "#{__FILE__}:#{__LINE__-3}:in 'Kernel#raise': exception message (Polyglot::ForeignException)\n"
    }
  end

  guard -> { !TruffleRuby.native? } do
    it "supports #full_message for a host exception" do
      integer = Java.type("java.lang.Integer")
      -> {
        integer.parseInt("abc")
      }.should raise_error(Polyglot::ForeignException) { |e|
        full_message = e.full_message(highlight: false, order: :top).gsub(/:\d+:/, ':LINE:')
        full_message.should.start_with?(<<~EXCEPTION)
        NumberFormatException.java:LINE:in 'forInputString': For input string: "abc" (Polyglot::ForeignException: java.lang.NumberFormatException)
        \tfrom Integer.java:LINE:in 'parseInt'
        \tfrom Integer.java:LINE:in 'parseInt'
        \tfrom #{__FILE__ }:LINE:in 'block (4 levels) in <top (required)>'
        EXCEPTION
      }
    end
  end

  it "supports rescue Polyglot::ForeignException" do
    begin
      raise @foreign
    rescue Polyglot::ForeignException => e
      e.should.equal?(@foreign)
    end
  end

  it "supports rescue Exception" do
    begin
      raise @foreign
    rescue Exception => e # rubocop:disable Lint/RescueException
      e.should.equal?(@foreign)
    end
  end

  it "supports rescue Object" do
    begin
      raise @foreign
    rescue Object => e
      e.should.equal?(@foreign)
    end
  end

  it "supports rescue class" do
    begin
      raise @foreign
    rescue @foreign.class => e
      e.should.equal?(@foreign)
    end
  end

  it "supports #raise" do
    -> { raise @foreign }.should raise_error(Polyglot::ForeignException) { |e|
      e.should.equal?(@foreign)
    }
  end

  it "supports #backtrace" do
    @foreign.backtrace.should.is_a?(Array)
    @foreign.backtrace.should_not.empty?
    @foreign.backtrace.each { |entry| entry.should.is_a?(String) }
  end

  it "supports #backtrace_locations" do
    @foreign.backtrace_locations.should.is_a?(Array)
    @foreign.backtrace_locations.should_not.empty?
    @foreign.backtrace_locations.each do |entry|
      entry.should.respond_to?(:absolute_path)
      entry.path.should.is_a?(String)
      entry.lineno.should.is_a?(Integer)
      entry.label.should.is_a?(String)
    end
  end

  it 'cannot be marshaled' do
    -> {
      Marshal.dump(@foreign)
    }.should raise_error(TypeError)
  end

  describe "when reaching the top-level" do
    it "is printed like a Ruby exception" do
      out = ruby_exe('raise Truffle::Debug.foreign_exception "main"', args: "2>&1", exit_status: 1, escape: false)
      out.should == "-e:1:in 'Kernel#raise': main (Polyglot::ForeignException)\n" \
        "\tfrom -e:1:in '<main>'\n"

      out = ruby_exe('at_exit { raise Truffle::Debug.foreign_exception "at exit" }', args: "2>&1", exit_status: 1, escape: false)
      out.should == "-e:1:in 'Kernel#raise': at exit (Polyglot::ForeignException)\n" \
        "\tfrom -e:1:in 'block in <main>'\n"
    end
  end
end
