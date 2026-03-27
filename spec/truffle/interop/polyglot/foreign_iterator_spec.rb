# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../../../ruby/spec_helper'

describe "Polyglot::ForeignIterator" do
  it "supports iteration by having IteratorTrait#each before IterableTrait#each when is has both iterable and iterator traits" do
    foreign = Truffle::Debug.foreign_iterator_iterable
    elements = []
    foreign.each { |e| elements << e }
    elements.should == [1, 2, 3]
  end

  it "includes Enumerable" do
    Truffle::Debug.foreign_iterator.should.is_a?(Enumerable)
    Truffle::Debug.foreign_iterable.should.is_a?(Enumerable)

    Truffle::Debug.foreign_iterator.select(&:odd?).should == [1, 3]
    Truffle::Debug.foreign_iterable.select(&:odd?).should == [1, 3]
  end
end
