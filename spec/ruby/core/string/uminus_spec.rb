# frozen_string_literal: false
require_relative '../../spec_helper'
require_relative 'fixtures/classes'

describe 'String#-@' do
  it 'returns self if the String is frozen' do
    input  = 'foo'.freeze
    output = -input

    output.should.equal?(input)
    output.should.frozen?
  end

  it 'returns a frozen copy if the String is not frozen' do
    input  = 'foo'
    output = -input

    output.should.frozen?
    output.should_not.equal?(input)
    output.should == 'foo'
  end

  it 'returns self if the String is a frozen subclass instance' do
    input  = StringSpecs::MyString.new('foo').freeze
    output = -input

    output.should.equal?(input)
  end

  it 'returns a frozen copy of the subclass instance if the String is an unfrozen subclass instance' do
    input  = StringSpecs::MyString.new('foo')
    output = -input

    output.should.frozen?
    output.should_not.equal?(input)
    output.should.instance_of?(StringSpecs::MyString)
    output.should == 'foo'
  end

  it 'returns a frozen copy even if the subclass overrides freeze' do
    cls = Class.new(String) do
      def freeze
        self
      end
    end
    input = cls.new('foo')
    output = -input

    output.should.frozen?
    output.should_not.equal?(input)
    output.should.instance_of?(cls)
    output.should == 'foo'
  end

  it 'returns a frozen copy if an unfrozen subclass instance overrides frozen? to return true' do
    cls = Class.new(String) do
      def frozen?
        true
      end
    end
    input = cls.new('foo')
    output = -input

    output.should_not.equal?(input)
    output.should.instance_of?(cls)
    output.should == 'foo'
    -> { output << 'bar' }.should.raise(FrozenError)
  end

  it 'returns self if a frozen subclass instance overrides frozen? to return false' do
    cls = Class.new(String) do
      def frozen?
        false
      end
    end
    input = cls.new('foo').freeze

    (-input).should.equal?(input)
  end

  it 'does not copy instance variables of an unfrozen subclass instance' do
    input = StringSpecs::MyString.new('foo')
    input.instance_variable_set(:@a, 1)

    (-input).instance_variables.should == []
  end

  it 'does not deduplicate equal unfrozen subclass instances' do
    (-StringSpecs::MyString.new('foo')).should_not.equal?(-StringSpecs::MyString.new('foo'))
  end

  it "returns the same object for equal unfrozen strings" do
    origin = "this is a string"
    dynamic = %w(this is a string).join(' ')

    origin.should_not.equal?(dynamic)
    (-origin).should.equal?(-dynamic)
  end

  it "returns the same object when it's called on the same String literal" do
    (-"unfrozen string").should.equal?(-"unfrozen string")
    (-"unfrozen string").should_not.equal?(-"another unfrozen string")
  end

  it "deduplicates frozen strings" do
    dynamic = %w(this string is frozen).join(' ').freeze

    dynamic.should_not.equal?("this string is frozen".freeze)

    (-dynamic).should.equal?("this string is frozen".freeze)
    (-dynamic).should.equal?((-"this string is frozen").freeze)
  end

  it "does not deduplicate a frozen string when it has instance variables" do
    dynamic = %w(this string is frozen).join(' ')
    dynamic.instance_variable_set(:@a, 1)
    dynamic.freeze

    (-dynamic).should_not.equal?("this string is frozen".freeze)
    (-dynamic).should_not.equal?((-"this string is frozen").freeze)
    (-dynamic).should.equal?(-dynamic)
  end

  it "deduplicates an unfrozen string with instance variables without copying them" do
    dynamic = %w(this string has instance variables).join(' ')
    dynamic.instance_variable_set(:@a, 1)
    output = -dynamic

    output.should.equal?(-"this string has instance variables")
    output.instance_variables.should == []
  end

  it "does not deduplicate a frozen string when it has a singleton class" do
    with_method = %w(this string is frozen).join(' ')
    def with_method.foo; end
    with_method.freeze

    extended = %w(this string is frozen).join(' ')
    extended.extend(Module.new)
    extended.freeze

    (-with_method).should.equal?(with_method)
    (-extended).should.equal?(extended)
  end

  it "deduplicates an unfrozen string with a singleton class without copying it" do
    dynamic = %w(this string has a singleton class).join(' ')
    def dynamic.foo; end
    output = -dynamic

    output.should.equal?(-"this string has a singleton class")
    output.singleton_methods.should == []
  end
end
