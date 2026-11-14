# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../ruby/optional/capi/spec_helper'

load_extension("truffleruby")

describe "C-API TRUFFLERUBY macro" do
  before :each do
    @s = CApiTruffleRubySpecs.new
  end

  it "is defined" do
    @s.truffleruby.should == true
  end
end

describe "C-API T_DATA objects" do
  before :each do
    @s = CApiTruffleRubySpecs.new
  end

  it "can be created for subclasses of Object and BasicObject" do
    [Object, BasicObject, Class.new, Class.new(BasicObject), Class.new(Numeric)].each do |klass|
      Kernel.instance_method(:class).bind_call(@s.typed_data_wrap(klass)).should == klass
      Kernel.instance_method(:class).bind_call(@s.data_wrap(klass)).should == klass
    end
  end

  it "have instance variables" do
    obj = @s.typed_data_wrap(Object)
    obj.instance_variable_set(:@foo, 42)
    obj.instance_variable_get(:@foo).should == 42
  end

  it "cannot be created for classes inheriting from a core class with its own internal representation" do
    [String, Array, Hash, Exception, Class.new(StandardError), Class.new(Proc), Class.new(Time)].each do |klass|
      -> { @s.typed_data_wrap(klass) }.should.raise(TypeError, /cannot create a T_DATA object of class/)
      -> { @s.data_wrap(klass) }.should.raise(TypeError, /cannot create a T_DATA object of class/)
    end
  end
end
