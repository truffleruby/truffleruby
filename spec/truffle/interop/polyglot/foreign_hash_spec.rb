# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2021-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignHash" do
  before :each do
    @hash = Truffle::Debug.foreign_hash
  end

  it "supports #length and #size" do
    @hash.size.should == 2
    @hash.length.should == 2
  end

  it "supports #empty?" do
    @hash.should_not.empty?
  end

  it "supports #[]" do
    @hash[:a].should == 1
    @hash[:b].should == 2
    @hash[:c].should == nil
  end

  it "supports #fetch" do
    @hash.fetch(:a) { flunk }.should == 1
    @hash.fetch(:a, 0).should == 1

    -> { @hash.fetch(:c) }.should raise_error(KeyError)
    @hash.fetch(:c, :default).should == :default
    @hash.fetch(:c) { |key| [key, :block] }.should == [:c, :block]
  end

  it "supports #each" do
    @hash.each do |key, value|
      if key == :a
        value.should == 1
      else
        key.should == :b
        value.should == 2
      end
    end
  end

  it "supports #each_pair" do
    @hash.each_pair do |key, value|
      if key == :a
        value.should == 1
      else
        key.should == :b
        value.should == 2
      end
    end
  end

  it "supports #each_key and #keys" do
    @hash.each_key.to_a.should == [:a, :b]
    @hash.keys.should == [:a, :b]
  end

  it "supports #each_value" do
    @hash.each_value.to_a.should == [1, 2]
    @hash.values.to_a.should == [1, 2]
  end

  it "supports #to_hash and #to_h" do
    @hash.to_hash.should == { a: 1, b: 2 }
    @hash.to_h.should == { a: 1, b: 2 }
  end

  it "returns an Enumerator for #each with no block" do
    enum = @hash.each
    enum.class.should == Enumerator
    enum.each do |key, value|
      if key == :a
        value.should == 1
      else
        key.should == :b
        value.should == 2
      end
    end
  end
end
