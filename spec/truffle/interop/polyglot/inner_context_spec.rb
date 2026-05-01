# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'
require_relative '../fixtures/classes'

describe "Polyglot::InnerContext" do
  it "creates an isolated inner context" do
    TruffleInteropSpecs::OuterContextConstant = :outer

    Polyglot::InnerContext.new do |context|
      context.eval('ruby', "InnerContextConstant = :inner")
      context.eval('ruby', "!!defined?(TruffleInteropSpecs::OuterContextConstant)").should == false
      context.eval('ruby', "!!defined?(InnerContextConstant)").should == true

      defined?(TruffleInteropSpecs::OuterContextConstant).should == "constant"
      defined?(InnerContextConstant).should == nil
    end
  end

  it "can specify languages explicitly" do
    Polyglot::InnerContext.new(languages: ['ruby']) do |context|
      context.eval('ruby', '6 * 7').should == 42
    end
  end

  it "can specify language options" do
    Polyglot::InnerContext.new(language_options: { 'ruby.debug' => 'true' }) do |context|
      context.eval('ruby', '$DEBUG').should == true
    end
  end

  it "can specify whether to inherit access" do
    Polyglot::InnerContext.new(inherit_all_access: false, language_options: { 'ruby.core-load-path' => 'resource:/truffleruby' }) do |context|
      context.eval('ruby', 'Truffle::POSIX::NATIVE').should == false
    end
  end

  it "treats Ruby objects from the inner context as foreign" do
    Polyglot::InnerContext.new do |context|
      obj = context.eval('ruby', "Object.new")
      Truffle::Interop.should.foreign?(obj)
      obj.to_s.should.start_with?("#<Polyglot::ForeignObject[Ruby] #<Object:")
      obj.inspect.should.start_with?("#<Polyglot::ForeignObject[Ruby] Object:")
    end
  end

  it "treats exceptions from the inner context as foreign" do
    Polyglot::InnerContext.new do |context|
      -> {
        context.eval('ruby', "raise 'foo'")
      }.should.raise(Polyglot::ForeignException, "foo")
    end
  end

  it "correctly unwraps an object from the outer context passed to the inner context and back" do
    Polyglot::InnerContext.new do |context|
      get, set = context.eval('ruby', "var = nil; [-> { var }, -> value { var = value }]")
      obj = Object.new
      set.call(obj)
      get.call.class.should.equal?(obj.class)
      get.call.should.equal?(obj)
    end
  end

  it "correctly unwraps an exception from the outer context passed to the inner context and back" do
    outer_context_exception = Class.new(RuntimeError)

    Polyglot::InnerContext.new do |context|
      callback = context.eval('ruby', "-> callback { callback.call }")
      -> {
        callback.call(-> { raise outer_context_exception, "outer context exception" })
      }.should.raise(outer_context_exception, "outer context exception")
    end
  end

  it "raises RuntimeError when trying to access an inner context exception if the inner context is closed" do
    -> {
      begin
        Polyglot::InnerContext.new do |context|
          context.eval('ruby', "raise 'foo'")
        end
      rescue Polyglot::ForeignException # This accesses the inner context exception, which raises the RuntimeError below
        raise "should not reach here"
      end
    }.should.raise(RuntimeError, 'This Polyglot::InnerContext has been closed, cannot use it anymore')
  end

  it "raises RuntimeError for if the InnerContext is closed" do
    context = nil
    obj = nil
    Polyglot::InnerContext.new do |c|
      context = c
      obj = context.eval('ruby', 'Object.new')
    end

    -> {
      context.eval('ruby', '42')
    }.should.raise(RuntimeError, 'This Polyglot::InnerContext has been closed, cannot use it anymore')

    -> {
      obj.object_id
    }.should.raise(RuntimeError, 'This Polyglot::InnerContext has been closed, cannot use it anymore')
  end

  it "raises ArgumentError for an unknown language" do
    Polyglot::InnerContext.new do |context|
      -> { context.eval('does_not_exist', '') }.should.raise(ArgumentError, 'Unknown language: does_not_exist')
    end
  end

  it "raises a RuntimeError when stopped" do
    context = Polyglot::InnerContext.new
    context.eval('ruby', '42') # Eagerly initializes the context to avoid stopping during context initialization

    in_synchronize = false
    th = Thread.new do
      Thread.pass until in_synchronize
      context.stop
    end
    -> {
      in_synchronize = true
      context.eval('ruby', 'loop { }')
    }.should.raise(RuntimeError, 'This Polyglot::InnerContext was terminated forcefully, cannot use it anymore')
    th.join
  end

  it "calls the given on_cancelled proc when stopped" do
    custom_error = Class.new(StandardError)
    context = Polyglot::InnerContext.new(on_cancelled: -> { raise custom_error, 'error message' })
    context.eval('ruby', '42') # Eagerly initializes the context to avoid stopping during context initialization

    in_synchronize = false
    th = Thread.new do
      Thread.pass until in_synchronize
      context.stop
    end
    -> {
      in_synchronize = true
      context.eval('ruby', 'loop { }')
    }.should.raise(custom_error, 'error message')
    th.join
  end
end
