# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2023-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# truffleruby_primitives: true

require_relative '../ruby/spec_helper'

unless defined?(::TruffleRuby)
  module Primitive
    def self.arguments_descriptor
      nil
    end

    def self.arguments
      nil
    end
  end
end

module ArgumentsDescriptorSpecs
  class Info
    attr_reader :values, :descriptor, :arguments

    def initialize(values, descriptor, arguments)
      @values = values
      @descriptor = descriptor
      @arguments = arguments
    end
  end
end

describe "Arguments descriptors" do
  def truffleruby?
    defined?(::TruffleRuby)
  end

  def info(values, descriptor, arguments)
    ArgumentsDescriptorSpecs::Info.new(values, descriptor, arguments)
  end

  guard -> { truffleruby? } do
    it "reflect if the caller passes keywords arguments, regardless of the callee" do
      def descriptor(*)
        Primitive.arguments_descriptor
      end

      descriptor().should == []
      descriptor(1).should == []
      splat = [1, 2]
      descriptor(*splat).should == []

      descriptor(a: 1).should == [:keywords, :a]
      kw = { b: 2 }
      descriptor(**kw).should == [:keywords]
      empty_kw = {}
      descriptor(**empty_kw).should == []
    end
  end

  def only_kws(a:, b:)
    info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
  end

  it "are empty for simple calls" do
    def no_kws(a, b)
      info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = no_kws(1, 2)
    info.values.should == [1, 2]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [1, 2] if truffleruby?
  end

  it "contain keyword arguments" do
    info = only_kws(a: 1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "contain optional keyword arguments" do
    def only_kws_and_opt(a:, b: 101)
      info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = only_kws_and_opt(a: 1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?

    info = only_kws_and_opt(a: 1)
    info.values.should == [1, 101]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?
  end

  it "contain keyword arguments with positional arguments" do
    def posn_kws(a, b:)
      info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = posn_kws(1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :b] if truffleruby?
    info.arguments.should == [1, {b: 2}] if truffleruby?
  end

  def posn_kws_defaults(a, b: 101, c: 102)
    info([a, b, c], Primitive.arguments_descriptor, Primitive.arguments)
  end

  it "contain optional keyword arguments with positional arguments" do
    info = posn_kws_defaults(1, b: 2, c: 3)
    info.values.should == [1, 2, 3]
    info.descriptor.should == [:keywords, :b, :c] if truffleruby?
    info.arguments.should == [1, {b: 2, c: 3}] if truffleruby?
  end

  it "do not contain missing optional keyword arguments" do
    info = posn_kws_defaults(1, b: 2)
    info.values.should == [1, 2, 102]
    info.descriptor.should == [:keywords, :b] if truffleruby?
    info.arguments.should == [1, {b: 2}] if truffleruby?

    info = posn_kws_defaults(1, c: 3)
    info.values.should == [1, 101, 3]
    info.descriptor.should == [:keywords, :c] if truffleruby?
    info.arguments.should == [1, {c: 3}] if truffleruby?
  end

  it "do not contain the name of distant explicitly splatted keyword arguments" do
    distant = {b: 2}
    info = only_kws(a: 1, **distant)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "do not contain the name of near explicitly splatted keyword arguments" do
    info = only_kws(a: 1, **{b: 2})
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "contain the name of present optional keyword arguments without the optional positional" do
    def opt_and_kws(a, b = 2, c: nil)
      info([a, b, c], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = opt_and_kws(1, c: 3)
    info.values.should == [1, 2, 3]
    info.descriptor.should == [:keywords, :c] if truffleruby?
    info.arguments.should == [1, {c: 3}] if truffleruby?
  end

  it "handle * and ** at the same call site" do
    def rest_kwrest(*a)
      info(a, Primitive.arguments_descriptor, Primitive.arguments)
    end

    a = []
    empty = {}
    info = rest_kwrest(*a, **empty)
    info.values.should == []
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [] if truffleruby?

    info = rest_kwrest(*a, 42, **empty)
    info.values.should == [42]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [42] if truffleruby?
  end

  it "do not consider kwargs inside an array element" do
    def nested_kwargs(*a, **kw)
      kw.should.empty?
      info(a, Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = nested_kwargs(["a" => 1])
    info.values.should == [[{"a" => 1}]]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [[{"a" => 1}]] if truffleruby?

    info = nested_kwargs([a: 1])
    info.values.should == [[{a: 1}]]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [[{a: 1}]] if truffleruby?

    array = [1]
    info = nested_kwargs([*array, :in => "out"])
    info.values.should == [[1, {:in=>"out"}]]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [[1, {:in=>"out"}]] if truffleruby?
  end

  it "work for a call like our Struct.new" do
    def struct_new_like(a, *b, c: 101)
      info([a, b, c], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = struct_new_like('A', :a, :b)
    info.values.should == ['A', [:a, :b], 101]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == ['A', :a, :b] if truffleruby?

    info = struct_new_like('A', :a, :b, c: 1)
    info.values.should == ['A', [:a, :b], 1]
    info.descriptor.should == [:keywords, :c] if truffleruby?
    info.arguments.should == ['A', :a, :b, {c: 1}] if truffleruby?

    info = struct_new_like('A', :a, :b, {c: 1})
    info.values.should == ['A', [:a, :b, {c: 1}], 101]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == ['A', :a, :b, {c: 1}] if truffleruby?

    info = struct_new_like('A', :a, :b, **{c: 1})
    info.values.should == ['A', [:a, :b], 1]
    info.descriptor.should == [:keywords] if truffleruby?
    info.arguments.should == ['A', :a, :b, {c: 1}] if truffleruby?

    distant = {c: 1}
    info = struct_new_like('A', :a, :b, **distant)
    info.values.should == ['A', [:a, :b], 1]
    info.descriptor.should == [:keywords] if truffleruby?
    info.arguments.should == ['A', :a, :b, {c: 1}] if truffleruby?

    -> { struct_new_like('A', :a, :b, d: 1) }.should raise_error(ArgumentError)

    -> { struct_new_like('A', :a, :b, **{d: 1}) }.should raise_error(ArgumentError)

    distant = {d: 1}
    -> { struct_new_like('A', :a, :b, **distant) }.should raise_error(ArgumentError)
  end

  it "work for a call like a Struct's new" do
    klass = Struct.new(:a)
    a = klass.new({b: [1, 2, 3]})
    a.a.should == {b: [1, 2, 3]}
    b = klass.new(a)
    b.a.should == a
    a.dig(:a, :b).should == [1, 2, 3]
  end

  it "work for a call like MSpec #describe" do
    def describe_like(a, b = nil)
      info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = describe_like(1, b: 2)
    info.values.should == [1, {b: 2}]
    info.descriptor.should == [:keywords, :b] if truffleruby?
    info.arguments.should == [1, {b: 2}] if truffleruby?
  end

  it "expand a hash not used for keyword arguments" do
    def single(a)
      info([a], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = single({a: 1, b: 2})
    info.values.should == [{a: 1, b: 2}]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "contain keyword argument names passed to a rest" do
    def rest(*a)
      info([a], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = rest(a: 1, b: 2)
    info.values.should == [[{a: 1, b: 2}]]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "contain keyword argument names passed to a keyword rest" do
    def kw_rest(**a)
      info([a], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = kw_rest(a: 1, b: 2)
    info.values.should == [{a: 1, b: 2}]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "contain keyword argument names passed to a combined keyword rest" do
    def kw_and_kw_rest(a:, **b)
      info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = kw_and_kw_rest(a: 1)
    info.values.should == [1, {}]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?

    info = kw_and_kw_rest(a: 1, b: 2, c: 3)
    info.values.should == [1, {b: 2, c: 3}]
    info.descriptor.should == [:keywords, :a, :b, :c] if truffleruby?
    info.arguments.should == [{a: 1, b: 2, c: 3}] if truffleruby?

    info = kw_and_kw_rest("abc" => 123, a: 1, b: 2)
    info.values.should == [1, {"abc" => 123, b: 2}]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{"abc" => 123, a: 1, b: 2}] if truffleruby?
  end

  it "work for a mixture of arguments" do
    def mixture(a, b = nil, c = nil, d, e: nil, **f)
      info([a, b, c, d, e, f], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = mixture(1, 2)
    info.values.should == [1, nil, nil, 2, nil, {}]
    info.descriptor.should == []  if truffleruby?
    info.arguments.should == [1, 2] if truffleruby?

    info = mixture(1, 2, e: 3)
    info.values.should == [1, nil, nil, 2, 3, {}]
    info.descriptor.should == [:keywords, :e] if truffleruby?
    info.arguments.should == [1, 2, {e: 3}] if truffleruby?

    info = mixture(1, 2, {foo: :bar})
    info.values.should == [1, 2, nil, {foo: :bar}, nil, {}]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [1, 2, {foo: :bar}] if truffleruby?

    info = mixture(1, {foo: :bar})
    info.values.should == [1, nil, nil, {foo: :bar}, nil, {}]
    info.descriptor.should == [] if truffleruby?
    info.arguments.should == [1, {foo: :bar}] if truffleruby?
  end

  it "work through an inlined call abstraction" do
    foo = -> (a:) { a }
    foo.(a: 1).should == 1
    -> { foo.() }.should raise_error(ArgumentError)
  end

  guard -> { truffleruby? } do
    it "pass keyword arguments into foreign calls as a Hash" do
      Truffle::Debug.foreign_identity_function.call(a: 1, b: 2).should == {a: 1, b: 2}
    end
  end

  it "work through custom new with keyword arguments" do
    new_kw = Class.new do
      def self.new(a:, b:)
        ArgumentsDescriptorSpecs::Info.new([a, b], Primitive.arguments_descriptor, Primitive.arguments)
      end
    end

    info = new_kw.new(a: 1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "work through custom new with rest" do
    new_rest = Class.new do
      def self.new(*a)
        ArgumentsDescriptorSpecs::Info.new([a], Primitive.arguments_descriptor, Primitive.arguments)
      end
    end

    info = new_rest.new(a: 1, b: 2)
    info.values.should == [[{a: 1, b: 2}]]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "work through delegation, but by reifying the keyword argument hash" do
    def delegator(...)
      delegated(...)
    end

    def delegated(a:, b:)
      ArgumentsDescriptorSpecs::Info.new([a, b], Primitive.arguments_descriptor, Primitive.arguments)
    end

    info = delegator(a: 1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "work through implicit super, but by reifying the keyword argument hash" do
    parent = Class.new do
      def implicit_super(a:, b:)
        ArgumentsDescriptorSpecs::Info.new([a, b], Primitive.arguments_descriptor, Primitive.arguments)
      end
    end

    child = Class.new(parent) do
      def implicit_super(a:, b:)
        super
      end
    end

    info = child.new.implicit_super(a: 1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "work through explicit super" do
    parent = Class.new do
      def explicit_super(a:, b:)
        ArgumentsDescriptorSpecs::Info.new([a, b], Primitive.arguments_descriptor, Primitive.arguments)
      end
    end

    child = Class.new(parent) do
      def explicit_super(a:, b:)
        super(a: a, b: b)
      end
    end

    info = child.new.explicit_super(a: 1, b: 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "work through yield" do
    def yielder(a, b)
      yield(a: a, b: b)
    end

    def use_yielder(x, y)
      yielder(x, y) do |a:, b:|
        info([a, b], Primitive.arguments_descriptor, Primitive.arguments)
      end
    end

    info = use_yielder(1, 2)
    info.values.should == [1, 2]
    info.descriptor.should == [:keywords, :a, :b] if truffleruby?
    info.arguments.should == [{a: 1, b: 2}] if truffleruby?
  end

  it "work through instance_exec" do
    info = instance_exec(a: 1) do |*args, **kwargs|
      info([args, kwargs], Primitive.arguments_descriptor, Primitive.arguments)
    end
    info.values.should == [[], {a: 1}]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?
  end

  it "work through module_exec" do
    info = Module.new.module_exec(a: 1) do |*args, **kwargs|
      ArgumentsDescriptorSpecs::Info.new([args, kwargs], Primitive.arguments_descriptor, Primitive.arguments)
    end
    info.values.should == [[], {a: 1}]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?
  end

  it "work through Proc#call" do
    info = -> (*args, **kwargs) do
      ArgumentsDescriptorSpecs::Info.new([args, kwargs], Primitive.arguments_descriptor, Primitive.arguments)
    end.call(a: 1)
    info.values.should == [[], {a: 1}]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?
  end

  it "work through Thread#new, but by reifying the keyword argument hash" do
    info = Thread.new(a: 1) do |*args, **kwargs|
      ArgumentsDescriptorSpecs::Info.new([args, kwargs], Primitive.arguments_descriptor, Primitive.arguments)
    end.value
    info.values.should == [[], {a: 1}]
    info.descriptor.should == [:keywords] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?
  end

  it "work through Fiber#resume" do
    info = Fiber.new do |*args, **kwargs|
      ArgumentsDescriptorSpecs::Info.new([args, kwargs], Primitive.arguments_descriptor, Primitive.arguments)
    end.resume(a: 1)
    info.values.should == [[], {a: 1}]
    info.descriptor.should == [:keywords, :a] if truffleruby?
    info.arguments.should == [{a: 1}] if truffleruby?

    Fiber.new(&-> { true }).resume(**{}).should == true
  end

  # CRuby behavior: https://bugs.ruby-lang.org/issues/18621
  it "Fiber.yield loses the fact it was kwargs from Fiber#resume" do
    f = Fiber.new do
      args = Fiber.yield
      args
    end
    f.resume
    args = f.resume(a: 1)
    Hash.ruby2_keywords_hash?(args).should == false
    args.should == {a: 1}
  end
end
