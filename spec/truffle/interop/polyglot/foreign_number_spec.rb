# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2022-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignNumber" do
  before :each do
    @numbers = [
      [Truffle::Debug.foreign_boxed_value(42), 42],
      [Truffle::Debug.foreign_boxed_value(1 << 84), 1 << 84],
      [Truffle::Debug.foreign_boxed_value(3.14), 3.14],
    ]
  end

  it "supports #==" do
    @numbers.each do |foreign, ruby|
      foreign.should == ruby
      foreign.should == foreign
      ruby.should == foreign
    end
  end

  it "supports #+@" do
    @numbers.each do |foreign, ruby|
      (+foreign).should == (+ruby)
    end
  end

  it "supports #-@" do
    @numbers.each do |foreign, ruby|
      (-foreign).should == (-ruby)
    end
  end

  it "supports #+" do
    @numbers.each do |foreign, ruby|
      (foreign + 3).should == (ruby + 3)
      (3 + foreign).should == (3 + ruby)
    end
  end

  it "supports #-" do
    @numbers.each do |foreign, ruby|
      (foreign - 3).should == (ruby - 3)
      (3 - foreign).should == (3 - ruby)
    end
  end

  it "supports #*" do
    @numbers.each do |foreign, ruby|
      (foreign * 3).should == (ruby * 3)
      (3 * foreign).should == (3 * ruby)
    end
  end

  it "supports #/" do
    @numbers.each do |foreign, ruby|
      (foreign / 3).should == (ruby / 3)
      (3 / foreign).should == (3 / ruby)
    end
  end

  it "supports #**" do
    @numbers.each do |foreign, ruby|
      (foreign ** 2).should == (ruby ** 2)
    end
  end

  it "does not support odd? yet" do
    @numbers.each do |foreign, _ruby|
      -> { foreign.odd? }.should raise_error(Polyglot::UnsupportedMessageError)
    end
  end
end
