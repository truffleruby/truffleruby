# truffleruby_primitives: true

# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2018-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

# == Description
# The purpose of this file is to test the methods in the `Truffle::Interop` module, which are used to send interop
# messages to foreign object from Ruby code.
#
# This is called "matrix specs" because it tests all messages against all possible types that may receive that message.
#
# == Calling interop methods from Ruby
# Interop messages can be sent from ruby by using the methods in the `Interop` module. These methods will
# throw Ruby errors (on the left in the list below) which match the corresponding Java exception types (on the right)
# thrown by the message implementation. The translation is performed by a `TranslateInteropExceptionNode`.
#
# Polyglot::UnsupportedMessageError     <- UnsupportedMessageException
#   inherits from StandardError
# IndexError                            <- InvalidArrayIndexException
# NameError                             <- UnknownIdentifierException
#   NoMethodError if the message was invokeMember, NoMethodError inherits from NameError so it is fine
# ArgumentError                         <- ArityException
# TypeError                             <- UnsupportedTypeException
# KeyError                              <- UnknownKeyException
#
# The same rules will also apply when using some Ruby special forms on a foreign object (instead of going through
# the methods in the `Interop` module). For instance:
# - `foreign[-1] = :v` => IndexError if index is invalid
# - `foreign.call()`   => Polyglot::UnsupportedMessageError if not executable
# - `foreign.a_method` => NoMethodError if the member is not invocable
#
# See /doc/contributor/interop_implicit_api.md for more information about these special forms.
#
# == Dynamic Ruby Interop API
# Any Ruby object can implement (some of) the interop messages by implementing `polyglot_*` methods.
# See /doc/contributor/interop.md and /doc/contributor/interop_details.md.
#
# These methods will be implemented by the user, in Ruby. As the messages specify Java exceptions to be thrown,
# the user should instead throw the exceptions of the same name under the `Interop` module (e.g.
# `Interop::UnsupportedMessageException` for the Java `UnsupportedMesasageException`).
#
# These `Interop` exceptions inherit from `Interop::InteropException` (itself inheriting from `Exception`).
# This means they aren't caught by the default `rescue` statement, which only catches inheritors of `StandardError`.
# Note that `StandardError` is a `TruffleException`, and so it will bubble up to the Java level if raised.
#
# Note that Interop.invoke_member should check arity explicitly and raise `Interop::ArityException` if it is not
# what is expected.

require 'bigdecimal'

require_relative '../../ruby/spec_helper'
require_relative 'fixtures/classes'

class PolyglotMatcher
  def initialize(expected)
    @expected = expected
  end

  def matches?(actual)
    @actual = actual
    @actual == @expected || (@expected.is_a?(Symbol) && @actual == @expected.to_s)
  end

  def failure_message
    ["Expected #{MSpec.format(@actual)}",
     "to have same value and type as #{MSpec.format(@expected)}"]
  end

  def negative_failure_message
    ["Expected #{MSpec.format(@actual)}",
     "not to have same value or type as #{MSpec.format(@expected)}"]
  end
end

module MSpecMatchers
  private def polyglot_match(expected)
    PolyglotMatcher.new(expected)
  end
end

describe 'Interop:' do

  INSPECTION  = -> v { code v.inspect }
  AN_INSTANCE = -> v do
    class_name = v.class.name
    an         = class_name.start_with?('A', 'E', 'I', 'O', 'U')
    (an ? 'an ' : 'a ') + code(class_name)
  end
  DEFAULT     = Object.new

  def code(string)
    '`' + string + '`'
  end

  def bold(string)
    '**' + string + '**'
  end

  Subject = Struct.new(:constant_value, :value_constructor, :key, :name, :doc, :explanation) do
    def initialize(constant_value = nil, name: INSPECTION, doc: false, explanation: nil, &value_constructor)
      super constant_value, value_constructor, nil, name, doc, explanation

      if name.is_a? Proc
        self.name = self.name.call value
      else
        self.name ||= value.inspect
      end
    end

    class << self
      alias_method :call, :new
    end

    def value
      value_constructor ? value_constructor.call : constant_value
    end
  end

  Message = Struct.new(:name, :tests, :rest) do
    def initialize(name, *tests, rest)
      raise ArgumentError, name.inspect unless Symbol === name
      raise ArgumentError, tests.inspect unless tests.all? { |v| Test === v }
      raise ArgumentError, rest.inspect unless rest.nil? || (Test === rest && rest.subjects.empty?)
      super name, tests, rest
    end
  end

  Test = Struct.new(:description, :subjects_description, :subjects, :test, :mode) do
    def initialize(description, *subjects, mode: :single, &test)
      raise ArgumentError, description.inspect unless String === description
      raise ArgumentError, test.inspect unless Proc === test
      subjects_description = (subjects.shift if subjects.first.is_a? String)
      super description, subjects_description, subjects.map { |k| SUBJECTS.fetch(k) { EXTRA_SUBJECTS.fetch(k) } }, test, mode
    end

    def spec_describe(subject)
      this = self
      describe("#{subject.name} (#{subject.key.inspect}),") { this.spec_it subject }
    end

    def spec_it(subject)
      description = self.description
      test        = self.test

      case mode
      when :single
        it "it #{description}" do
          test.call subject.value
        end
      when :values
        SUBJECTS.each do |_, subject_as_value|
          it "it #{description} where value is #{subject_as_value.name} (#{subject_as_value.key.inspect})" do
            test.call subject.value, subject_as_value.value
          end
        end
      else
        raise ArgumentError, "unknown mode: #{mode.inspect}"
      end
    end
  end

  Delimiter = Struct.new(:text)

  array_polyglot_methods = %w[
      polyglot_has_array_elements?
      polyglot_array_size
      polyglot_read_array_element
      polyglot_write_array_element
      polyglot_remove_array_element
      polyglot_array_element_readable?
      polyglot_array_element_modifiable?
      polyglot_array_element_insertable?
      polyglot_array_element_removable?]

  member_polyglot_methods = %w[
      polyglot_has_members?
      polyglot_members
      polyglot_read_member
      polyglot_write_member
      polyglot_remove_member
      polyglot_invoke_member
      polyglot_member_readable?
      polyglot_member_modifiable?
      polyglot_member_removable?
      polyglot_member_insertable?
      polyglot_member_invocable?
      polyglot_member_internal?
      polyglot_has_member_read_side_effects?
      polyglot_has_member_write_side_effects?]

  pointer_polyglot_methods = %w[
      polyglot_pointer?
      polyglot_as_pointer
      polyglot_to_native]

  hash_polyglot_methods = %w[
      polyglot_has_hash_entries?
      polyglot_hash_size
      polyglot_hash_entry_existing?
      polyglot_hash_entry_insertable?
      polyglot_hash_entry_modifiable?
      polyglot_hash_entry_readable?
      polyglot_hash_entry_removable?
      polyglot_hash_entry_writable?
      polyglot_read_hash_entry
      polyglot_write_hash_entry
      polyglot_remove_hash_entry
      polyglot_hash_entries_iterator
      polyglot_hash_keys_iterator
      polyglot_hash_values_iterator]

  interop_library_reference = "  The methods correspond to messages defined in\n" +
      "  [InteropLibrary](https://www.graalvm.org/truffle/javadoc/com/oracle/truffle/api/interop/InteropLibrary.html)."

  # TODO (pitr-ch 20-Feb-2020): test objects from CExt ?
  # TODO (pitr-ch 13-Feb-2020): add number corner cases

  StructWithValue = Struct.new :value

  SUBJECTS = {
      nil:            Subject.(nil, doc: true),
      false:          Subject.(false, doc: true),
      true:           Subject.(true, doc: true),

      symbol:         Subject.(:symbol, doc: true),
      strange_symbol: Subject.(:"strange -=@\0x2397"),
      empty_string:   Subject.() { "" },
      string:         Subject.(name: AN_INSTANCE, doc: true) { "string" },
      frozen_string:  Subject.() { "frozen".freeze },

      zero:          Subject.(0),
      small_integer: Subject.(1, name: AN_INSTANCE, doc: true),
      long_integer:  Subject.(1 << 60),
      big_integer:   Subject.(1 << 80),
      zero_float:    Subject.(0.0),
      small_float:   Subject.(1.0, name: AN_INSTANCE, doc: true),
      big_decimal:   Subject.(BigDecimal('1e99'), name: AN_INSTANCE, doc: true),

      # Other immutables
      range:         Subject.(1..),
      int_range:     Subject.(1..2),
      long_range:    Subject.((1 << 60)..(1 << 61)),
      encoding:      Subject.(Encoding::UTF_8),
      regexp:        Subject.(/regexp/),

      object:        Subject.(name: AN_INSTANCE, doc: true) { Object.new },
      frozen_object: Subject.(name: "a frozen `Object`", doc: true) { Object.new.freeze },
      struct:        Subject.(name: AN_INSTANCE, doc: true, explanation: "a `Struct` with one property named `value`") { StructWithValue.new DEFAULT },
      class:         Subject.(name: AN_INSTANCE, doc: true) { Class.new },
      module:        Subject.(name: AN_INSTANCE) { Module.new },
      hash:          Subject.(name: AN_INSTANCE, doc: true) { {} },
      array:         Subject.(name: AN_INSTANCE, doc: true) { [] },
      # raise & rescue to give it a backtrace
      exception: Subject.(name: "an `Exception`", doc: true) do
        begin
          raise "the exception message"
        rescue => e
          e
        end
      end,
      exception_with_cause: Subject.(name: "an `Exception` with a cause", doc: true) do
        begin
          raise "the cause"
        rescue
          begin
            raise "the exception message"
          rescue => e
            e
          end
        end
      end,
      # also test RaiseException since it is what other languages see when they catch an exception from Ruby
      raise_exception: Subject.(name: AN_INSTANCE) do
        begin
          raise "the exception message"
        rescue => e
          Primitive.exception_get_raise_exception(e)
        end
      end,

      proc:          Subject.(proc { |v| v }, name: code("proc {...}"), doc: true),
      lambda:        Subject.(-> v { v }, name: code("lambda {...}"), doc: true),
      method:        Subject.new(Object.new.tap { |o| o.define_singleton_method(:foo) { |v| v } }.method(:foo),
                                 name: AN_INSTANCE, doc: true),

      pointer:          Subject.new(
          name:        AN_INSTANCE,
          doc:         true,
          explanation: "an object implementing the polyglot pointer API.") { Truffle::FFI::Pointer.new(0) },

      polyglot_pointer: Subject.new(
          name:        "polyglot pointer",
          doc:         true,
          explanation: "an object which implements the `polyglot_*` methods for pointer, which are:\n  " +
                           pointer_polyglot_methods.map { |m| code(m) }.join(",\n  ") + ".\n" +
                           interop_library_reference) { TruffleInteropSpecs::PolyglotPointer.new },

      polyglot_object:  Subject.new(
          doc:         true,
          name:        "polyglot members",
          explanation: "an object which implements the `polyglot_*` methods for members, which are:\n  " +
                           member_polyglot_methods.map { |m| code(m) }.join(",\n  ") + ".\n" +
                           interop_library_reference) { TruffleInteropSpecs::PolyglotMember.new },

      polyglot_array:   Subject.new(
          doc:         true,
          name:        "polyglot array",
          explanation: "an object which implements the `polyglot_*` methods for array elements, which are:\n  " +
                           array_polyglot_methods.map { |m| code(m) }.join(",\n  ") + ".\n" +
                           interop_library_reference) { TruffleInteropSpecs::PolyglotArray.new },

      polyglot_hash:    Subject.new(
        doc:         true,
        name:        "polyglot hash",
        explanation: "an object which implements the `polyglot_*` methods for hash elements, which are:\n  " +
          hash_polyglot_methods.map { |m| code(m) }.join(",\n  ") + ".\n" +
          interop_library_reference) { TruffleInteropSpecs::PolyglotHash.new }
  }.each { |key, subject| subject.key = key }

  immediate_subjects     = [:false, :true, :zero, :small_integer, :long_integer, :zero_float, :small_float]
  non_immediate_subjects = SUBJECTS.keys - immediate_subjects
  frozen_subjects        = SUBJECTS.each_pair.select { |key, subject| key != :raise_exception && subject.value.frozen? }.map(&:first)
  exception_subjects     = [:exception, :exception_with_cause, :raise_exception]

  # not part of the standard matrix, not considered in last rest case
  EXTRA_SUBJECTS = {
      polyglot_int_array: Subject.new(
          doc:         true,
          explanation: "an object which implements the `polyglot_*` methods for array elements allowing only Integers to be stored",
          name:        "polyglot int array") { TruffleInteropSpecs::PolyglotArray.new { |v| Integer === v } }
  }.each { |key, subject| subject.key = key }

  def predicate(name, is, *message_args, &setup)
    -> subject do
      setup.call subject if setup
      Truffle::Interop.send(name, subject, *message_args).should == is
    end
  end

  def unsupported_test(precise = true, &action)
    Test.new("fails with `UnsupportedMessageError`") do |subject|
      error_matcher = precise ? Polyglot::UnsupportedMessageError : -> v { Polyglot::UnsupportedMessageError === v || RuntimeError === v || TypeError === v }
      -> { action.call(subject) }.should raise_error(error_matcher, /Message not supported|unsupported message/)
    end
  end

  def array_element_predicate(message, predicate, insert_on_true_case)
    insert = -> subject { Truffle::Interop.write_array_element(subject, 0, Object.new) }
    Message[message,
            Test.new("returns true if there is #{insert_on_true_case ? 'a' : 'no'} value at the given index",
                     :array, :polyglot_array,
                     &predicate(predicate, true, 0, &(insert if insert_on_true_case))),
            Test.new("returns false if there is #{!insert_on_true_case ? 'a' : 'no'} value at the given index",
                     :array, :polyglot_array,
                     &predicate(predicate, false, 0, &(insert unless insert_on_true_case))),
            Test.new("returns false", &predicate(predicate, false, 0))]
  end

  MESSAGES = [
      Delimiter["toDisplayString()"],
      Message[:toDisplayString,
              Test.new("returns a String representing the object", :strange_symbol, :raise_exception) do |subject|
                Truffle::Interop.should.string? Truffle::Interop.to_display_string(subject)
              end,
              Test.new("returns the same as `subject.inspect`") do |subject|
                Truffle::Interop.to_display_string(subject).should == subject.inspect
              end],

      Delimiter["MetaObject related messages"],
      Message[:hasMetaObject,
              Test.new("returns true", *non_immediate_subjects, &predicate(:has_meta_object?, true)),
              Test.new("returns false", &predicate(:has_meta_object?, false))],
      Message[:getMetaObject,
              Test.new("returns the Ruby exception's class", :raise_exception) do |subject|
                Truffle::Interop.meta_object(subject).should == RuntimeError
              end,
              Test.new("returns the same Ruby class as `subject.class`") do |subject|
                # It works for immediate_subjects too because of Truffle::Interop.meta_object handling that
                Truffle::Interop.meta_object(subject).should == subject.class
              end],

      Delimiter["`null` related messages"],
      Message[:isNull,
              Test.new("returns true", :nil, &predicate(:null?, true)),
              Test.new("returns false", &predicate(:null?, false))],


      Delimiter["`boolean` related messages"],
      Message[:isBoolean,
              Test.new("returns true", :true, :false, &predicate(:boolean?, true)),
              Test.new("returns false", &predicate(:boolean?, false))],
      Message[:asBoolean,
              Test.new("returns the receiver", :true, :false) do |subject|
                Truffle::Interop.as_boolean(subject).should == subject
              end,
              unsupported_test { |subject| Truffle::Interop.as_boolean(subject) }],


      Delimiter["Messages related to executable objects"],
      Message[:isExecutable,
              Test.new("returns true", :proc, :lambda, :method, &predicate(:executable?, true)),
              Test.new("returns false", &predicate(:executable?, false))],
      Message[:execute,
              Test.new("returns the result of the execution", :proc, :lambda, :method) do |subject|
                value = Object.new
                Truffle::Interop.execute(subject, value).should == value
              end,
              Test.new("fails with `ArityException` when the number of arguments is wrong", :lambda, :method) do |subject|
                value = Object.new
                -> { Truffle::Interop.execute(subject, value, value) }.should raise_error(ArgumentError)
              end,
              Test.new("returns the result of the execution even though the number of arguments is wrong (Ruby behavior)", :proc) do |subject|
                value = Object.new
                Truffle::Interop.execute(subject, value, value).should == value
              end,
              unsupported_test { |subject| Truffle::Interop.execute(subject) }],

      Delimiter["Messages related to pointers"],
      Message[:isPointer,
              Test.new("returns true", :pointer, &predicate(:pointer?, true)),
              Test.new("returns false", &predicate(:pointer?, false))],
      Message[:asPointer,
              Test.new("returns the pointer address", :pointer) do |subject|
                Truffle::Interop.as_pointer(subject).should == 0
              end,
              unsupported_test { |subject| Truffle::Interop.as_pointer(subject) }],
      Message[:toNative,
              Test.new('converts the receiver to native and changes the value of isPointer from false to true if possible',
                       :pointer, :polyglot_pointer) do |subject|
                case subject
                when Truffle::FFI::Pointer
                  Truffle::Interop.pointer?(subject).should be_true
                when TruffleInteropSpecs::PolyglotPointer
                  Truffle::Interop.pointer?(subject).should_not be_true
                else
                  raise "unsupported subject"
                end
                Truffle::Interop.to_native(subject)
                Truffle::Interop.pointer?(subject).should be_true
                Truffle::Interop.as_pointer(subject).should be_kind_of(Integer)
              end,
              Test.new('does nothing') do |subject|
                Truffle::Interop.pointer?(subject).should_not be_true
                Truffle::Interop.to_native(subject)
                Truffle::Interop.pointer?(subject).should_not be_true
              end],

      Delimiter["Array related messages"],
      Message[:hasArrayElements,
              Test.new("returns true", :array, :polyglot_array, :polyglot_int_array, &predicate(:has_array_elements?, true)),
              Test.new("returns false", &predicate(:has_array_elements?, false))],
      Message[:getArraySize,
              Test.new("returns size of the array", :array, :polyglot_array) do |subject|
                Truffle::Interop.array_size(subject).should == 0
                value = Object.new
                Truffle::Interop.write_array_element(subject, 0, value)
                Truffle::Interop.array_size(subject).should == 1
                Truffle::Interop.remove_array_element(subject, 0)
                Truffle::Interop.array_size(subject).should == 0
              end,
              unsupported_test { |subject| Truffle::Interop.array_size(subject) }],
      Message[:readArrayElement,
              Test.new("returns the stored value when it is present at the given valid index (`0 <= index < size`)", :array, :polyglot_array, mode: :values) do |subject, value|
                Truffle::Interop.write_array_element(subject, 0, value)
                Truffle::Interop.read_array_element(subject, 0).should polyglot_match value
              end,
              Test.new("fails with `InvalidArrayIndexException` when a value is not present at the index or the index is invalid", :array, :polyglot_array) do |subject|
                -> { Truffle::Interop.read_array_element(subject, 0) }.should raise_error(IndexError)
                -> { Truffle::Interop.read_array_element(subject, -1) }.should raise_error(IndexError)
              end,
              unsupported_test { |subject| Truffle::Interop.read_array_element(subject, 0) }],
      Message[:writeArrayElement,
              Test.new("stores a value at a given index", :array, :polyglot_array, mode: :values) do |subject, value|
                Truffle::Interop.write_array_element(subject, 0, value)
                Truffle::Interop.read_array_element(subject, 0).should polyglot_match value
                Truffle::Interop.write_array_element(subject, 10, value)
                Truffle::Interop.read_array_element(subject, 10).should polyglot_match value
              end,
              Test.new("fails with `InvalidArrayIndexException` when a index is invalid", :array, :polyglot_array) do |subject|
                -> { Truffle::Interop.write_array_element(subject, -1, Object.new) }.should raise_error(IndexError)
              end,
              Test.new("fails with `UnsupportedTypeException` when the value is invalid", :polyglot_int_array) do |subject|
                Truffle::Interop.write_array_element(subject, 0, 42)
                Truffle::Interop.read_array_element(subject, 0).should == 42
                -> { Truffle::Interop.write_array_element(subject, 1, Object.new) }.should raise_error(TypeError)
              end,
              unsupported_test { |subject| Truffle::Interop.write_array_element(subject, 0, Object.new) }],
      Message[:removeArrayElement,
              Test.new("removes a value when the value is present at a valid index", :array, :polyglot_array) do |subject|
                value = Object.new
                Truffle::Interop.write_array_element(subject, 0, value)
                Truffle::Interop.remove_array_element(subject, 0)
                Truffle::Interop.array_element_readable?(subject, 0).should be_false
              end,
              Test.new("fails with `InvalidArrayIndexException` when the value is not present at a valid index", :array, :polyglot_array) do |subject|
                -> { Truffle::Interop.remove_array_element(subject, 0) }.should raise_error(IndexError)
              end,
              unsupported_test { |subject| Truffle::Interop.remove_array_element(subject, 0) }],
      array_element_predicate(:isArrayElementReadable, :array_element_readable?, true),
      array_element_predicate(:isArrayElementModifiable, :array_element_modifiable?, true),
      array_element_predicate(:isArrayElementInsertable, :array_element_insertable?, false),
      array_element_predicate(:isArrayElementRemovable, :array_element_removable?, true),

      Delimiter["Hash related messages"],
      Message[:hasHashEntries,
              Test.new("returns true", :hash, :polyglot_hash, &predicate(:has_hash_entries?, true)),
              Test.new("returns false", &predicate(:has_hash_entries?, false))],
      Message[:getHashSize,
              Test.new("returns size of the hash", :hash, :polyglot_hash) do |subject|
                Truffle::Interop.hash_size(subject).should == 0
                key = Object.new
                value = Object.new
                Truffle::Interop.write_hash_entry(subject, key, value)
                Truffle::Interop.hash_size(subject).should == 1
                Truffle::Interop.remove_hash_entry(subject, key)
                Truffle::Interop.hash_size(subject).should == 0
              end,
              unsupported_test { |subject| Truffle::Interop.hash_size(subject) }],
      Message[:isHashEntryExisting,
              Test.new("returns true if there is an entry for the key", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.hash_entry_existing?(subject, key).should == false
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.hash_entry_existing?(subject, key).should == true
              end,
              Test.new("returns false") { |subject| Truffle::Interop.hash_entry_existing?(subject, 0).should == false }],
      Message[:isHashEntryReadable,
              Test.new("returns true if there is a readable entry for the key", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.hash_entry_readable?(subject, key).should == false
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.hash_entry_readable?(subject, key).should == true
              end,
              Test.new("returns false") { |subject| Truffle::Interop.hash_entry_readable?(subject, 0).should == false }],
      Message[:isHashEntryInsertable,
              Test.new("returns true if the key can be inserted", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.hash_entry_insertable?(subject, key).should == true
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.hash_entry_insertable?(subject, key).should == false
              end,
              Test.new("returns false") { |subject| Truffle::Interop.hash_entry_insertable?(subject, 0).should == false }],
      Message[:isHashEntryRemovable,
              Test.new("returns true if there is a removable entry for the key", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.hash_entry_removable?(subject, key).should == false
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.hash_entry_removable?(subject, key).should == true
              end,
              Test.new("returns false") { |subject| Truffle::Interop.hash_entry_removable?(subject, 0).should == false }],
      Message[:isHashEntryModifiable,
              Test.new("returns true if there is a modifiable entry for the key", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.hash_entry_modifiable?(subject, key).should == false
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.hash_entry_modifiable?(subject, key).should == true
              end,
              Test.new("returns false") { |subject| Truffle::Interop.hash_entry_modifiable?(subject, 0).should == false }],
      Message[:isHashEntryWritable,
              Test.new("returns true if the key can be inserted or there is a modifiable entry for it", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.hash_entry_writable?(subject, key).should == true
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.hash_entry_writable?(subject, key).should == true
              end,
              Test.new("returns false") { |subject| Truffle::Interop.hash_entry_writable?(subject, 0).should == false }],
      Message[:readHashValue,
              Test.new("reads an entry that exists", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.read_hash_value(subject, key).should polyglot_match key
              end,
              Test.new("fails with KeyError if the entry does not exist", :hash, :polyglot_hash, mode: :values) do |subject, key|
                -> { Truffle::Interop.read_hash_value(subject, key) }.should raise_error(KeyError)
              end,
              unsupported_test { |subject| Truffle::Interop.read_hash_value(subject, 0) }],
      Message[:writeHashEntry,
              Test.new("writes entries, new and existing", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.read_hash_value(subject, key).should polyglot_match key
                Truffle::Interop.write_hash_entry(subject, key, key.to_s)
                Truffle::Interop.read_hash_value(subject, key).should polyglot_match key.to_s
              end,
              unsupported_test { |subject| Truffle::Interop.write_hash_entry(subject, 0, 0) }],
      Message[:removeHashEntry,
              Test.new("removes existing entries", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.write_hash_entry(subject, key, key)
                Truffle::Interop.remove_hash_entry(subject, key)
                Truffle::Interop.hash_entry_existing?(subject, key).should == false
              end,
              Test.new("fails with KeyError if the entry does not exist", :hash, :polyglot_hash, mode: :values) do |subject, key|
                -> { Truffle::Interop.read_hash_value(subject, key) }.should raise_error(KeyError)
              end,
              unsupported_test { |subject| Truffle::Interop.remove_hash_entry(subject, 0) }],
      Message[:getHashEntriesIterator,
              Test.new("retrieves an entry iterator", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.write_hash_entry(subject, 0, key)
                Truffle::Interop.write_hash_entry(subject, 1, key)
                iterator = Truffle::Interop.hash_entries_iterator(subject)
                Truffle::Interop.has_iterator_next_element?(iterator).should == true
                Truffle::Interop.iterator_next_element(iterator).should polyglot_match [0, key]
                Truffle::Interop.has_iterator_next_element?(iterator).should == true
                Truffle::Interop.iterator_next_element(iterator).should polyglot_match [1, key]
                Truffle::Interop.has_iterator_next_element?(iterator).should == false
              end,
              Test.new("fails with StopIterationException if the hash is empty", :hash, :polyglot_hash) do |subject|
                iterator = Truffle::Interop.hash_entries_iterator(subject)
                -> { Truffle::Interop.iterator_next_element(iterator) }.should raise_error(StopIteration)
              end,
              unsupported_test { |subject| Truffle::Interop.hash_entries_iterator(subject) }],
      Message[:getHashKeysIterator,
              Test.new("retrieves a key iterator", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.write_hash_entry(subject, key, 0)
                Truffle::Interop.write_hash_entry(subject, 42, 43)
                iterator = Truffle::Interop.hash_keys_iterator(subject)
                Truffle::Interop.has_iterator_next_element?(iterator).should == true
                Truffle::Interop.iterator_next_element(iterator).should polyglot_match key
                Truffle::Interop.has_iterator_next_element?(iterator).should == true
                Truffle::Interop.iterator_next_element(iterator).should == 42
                Truffle::Interop.has_iterator_next_element?(iterator).should == false
              end,
              Test.new("fails with StopIterationException if the hash is empty", :hash, :polyglot_hash) do |subject|
                iterator = Truffle::Interop.hash_keys_iterator(subject)
                -> { Truffle::Interop.iterator_next_element(iterator) }.should raise_error(StopIteration)
              end,
              unsupported_test { |subject| Truffle::Interop.hash_entries_iterator(subject) }],
      Message[:getHashValuesIterator,
              Test.new("retrieves a value iterator", :hash, :polyglot_hash, mode: :values) do |subject, key|
                Truffle::Interop.write_hash_entry(subject, 0, key)
                Truffle::Interop.write_hash_entry(subject, 42, 43)
                iterator = Truffle::Interop.hash_values_iterator(subject)
                Truffle::Interop.has_iterator_next_element?(iterator).should == true
                Truffle::Interop.iterator_next_element(iterator).should polyglot_match key
                Truffle::Interop.has_iterator_next_element?(iterator).should == true
                Truffle::Interop.iterator_next_element(iterator).should == 43
                Truffle::Interop.has_iterator_next_element?(iterator).should == false
              end,
              Test.new("fails with StopIterationException if the hash is empty", :hash, :polyglot_hash) do |subject|
                iterator = Truffle::Interop.hash_values_iterator(subject)
                -> { Truffle::Interop.iterator_next_element(iterator) }.should raise_error(StopIteration)
              end,
              unsupported_test { |subject| Truffle::Interop.hash_entries_iterator(subject) }],

      Delimiter["Members related messages (incomplete)"],
      Message[:readMember,
              Test.new("returns a method with the given name when the method is defined", "any non-immediate `Object`",
                       *non_immediate_subjects - [:polyglot_object, :raise_exception]) do |subject|
                Truffle::Interop.read_member(subject, 'to_s').should == subject.method(:to_s)
              end,
              Test.new("fails with `UnknownIdentifierException` when the method is not defined", "any non-immediate `Object`",
                       *non_immediate_subjects - [:polyglot_object]) do |subject|
                -> { Truffle::Interop.read_member(subject, '__non_existing__') }.should raise_error(NameError)
              end,
              Test.new("reads the given instance variable", "any non-immediate `Object`",
                       *SUBJECTS.keys - immediate_subjects - frozen_subjects - [:polyglot_object]) do |subject|
                value = Object.new
                subject.instance_variable_set :@ivar, value
                Truffle::Interop.read_member(subject, '@ivar').should == value
              end,
              Test.new("reads the value stored with the given name", :polyglot_object) do |subject|
                value = Object.new
                Truffle::Interop.write_member(subject, 'key', value)
                Truffle::Interop.read_member(subject, 'key').should == value
              end,
              Test.new("returns the value of the given struct member", :struct) do |subject|
                Truffle::Interop.read_member(subject, 'value').should == DEFAULT
                subject.value = value = Object.new
                Truffle::Interop.read_member(subject, 'value').should == value
              end,
              unsupported_test { |subject| Truffle::Interop.read_member(subject, :any) }],
      Message[:writeMember,
              Test.new("writes the given instance variable", "any non-immediate non-frozen `Object`",
                       *SUBJECTS.keys - immediate_subjects - frozen_subjects - [:polyglot_object]) do |subject|
                value = Object.new
                Truffle::Interop.write_member(subject, '@ivar', value)
                subject.instance_variable_get(:@ivar).should == value
              end,
              Test.new("writes the given value under the given name", :polyglot_object) do |subject|
                value = Object.new
                Truffle::Interop.write_member(subject, 'key', value)
                Truffle::Interop.read_member(subject, 'key').should == value
              end,
              Test.new("writes the value to the given struct member", :struct) do |subject|
                value = Object.new
                Truffle::Interop.write_member(subject, 'value', value)
                subject.value.should == value
              end,
              Test.new("fails with `UnsupportedMessageError` when the receiver is frozen", *frozen_subjects) do |subject|
                -> { Truffle::Interop.write_member(subject, '@ivar', Object.new) }.should raise_error(Polyglot::UnsupportedMessageError)
              end,
              unsupported_test { |subject| Truffle::Interop.write_member(subject, :something, 'val') }],

      Delimiter["Exception related messages"],
      Message[:isException,
              Test.new("returns true", *exception_subjects, &predicate(:exception?, true)),
              Test.new("returns false", &predicate(:exception?, false))],
      Message[:throwException,
              Test.new("throws the exception", *exception_subjects) do |subject|
                -> { Truffle::Interop.throw_exception(subject) }.should raise_error { |e| e.should.equal?(subject) }
              end,
              unsupported_test { |subject| Truffle::Interop.throw_exception(subject) }],
      Message[:getExceptionType,
              Test.new("returns the exception type", *exception_subjects) do |subject|
                Truffle::Interop.exception_type(subject).should == :RUNTIME_ERROR
              end,
              unsupported_test { |subject| Truffle::Interop.exception_type(subject) }],
      Message[:hasExceptionMessage,
              Test.new("returns true", *exception_subjects,  &predicate(:has_exception_message?, true)),
              Test.new("returns false", &predicate(:has_exception_message?, false))],
      Message[:getExceptionMessage,
              Test.new("returns the message of the exception", *exception_subjects) do |subject|
                Truffle::Interop.exception_message(subject).should == "the exception message"
              end,
              unsupported_test { |subject| Truffle::Interop.exception_message(subject) }],
      Message[:hasExceptionStackTrace,
              Test.new("returns true", *exception_subjects,  &predicate(:has_exception_stack_trace?, true)),
              Test.new("returns false", &predicate(:has_exception_stack_trace?, false))],
      Message[:getExceptionStackTrace,
              Test.new("returns the stacktrace of the exception", *exception_subjects) do |subject|
                stacktrace = Truffle::Interop.exception_stack_trace(subject)
                Truffle::Interop.should.has_array_elements?(stacktrace)
              end,
              unsupported_test { |subject| Truffle::Interop.exception_stack_trace(subject) }],
      Message[:hasExceptionCause,
              Test.new("returns true", :exception_with_cause, &predicate(:has_exception_cause?, true)),
              Test.new("returns false", &predicate(:has_exception_cause?, false))],
      Message[:getExceptionCause,
              Test.new("returns the cause of the exception", :exception_with_cause) do |subject|
                Truffle::Interop.exception_cause(subject).should == subject.cause
              end,
              unsupported_test { |subject| Truffle::Interop.exception_cause(subject) }],

      Delimiter["Number related messages (missing)"],
      Delimiter["Instantiation related messages (missing)"],
      Delimiter["Time related messages (unimplemented)"],
  ]

  at_exit do
    md_path = File.join(__dir__, '..', '..', '..', 'doc', 'contributor', 'interop_details.md')
    if File.exist?(md_path) && File.writable?(md_path)
      File.open(md_path, 'w') do |out|
        out.print "<!-- Generated by spec/truffle/interop/matrix_spec.rb -->\n\n"
        out.print "# Detailed definition of polyglot behaviour is given for\n\n"

        { **SUBJECTS, **EXTRA_SUBJECTS }.each do |_, subject|
          next unless subject.doc
          out.print '- ' + bold(subject.name)
          out.puts subject.explanation ? (" – " + subject.explanation) : nil
        end

        out.puts
        out.puts "# Behavior of interop messages for Ruby objects"

        MESSAGES.each do |message|
          if message.is_a? Delimiter
            out.puts "\n## #{message.text}"
            next
          end
          out.puts "\nWhen interop message `#{message.name}` is sent"

          format_subjects = -> subjects {
            names = subjects.select(&:doc).map(&:name).map { |s| bold s }
            names = names[0...-2] + [names[-2..-1].join(' or ')] if names.size > 1
            names.join(', ')
          }
          message.tests.each do |test|
            subjects_description = (test.subjects_description + " like " if test.subjects_description)
            out.puts "- to #{subjects_description}" + format_subjects.call(test.subjects) + "\n  it " + test.description + "."
          end

          # remaining_keys = SUBJECTS.keys - message.tests.map(&:subjects).flatten
          # remaining_subjects = SUBJECTS.values_at(*remaining_keys)

          if message.rest
            out.puts "- otherwise\n  it " + message.rest.description + "."
          end
        end
      end
    end
  end

  def spec_describe(description, subject, test)
    describe "#{subject.name} (#{subject.key.inspect})," do
      it "it #{description}" do
        test.call subject.value
      end
    end
  end

  MESSAGES.each do |message|
    next if message.is_a? Delimiter

    tests = message.tests
    describe "When interop message #{message.name} is sent to" do
      subjects_tested = {}.compare_by_identity

      tests.each do |test|
        test.subjects.each do |subject|
          subjects_tested[subject.key] = subject

          test.spec_describe subject
        end
      end

      if message.rest
        SUBJECTS.each do |key, subject|
          next if subjects_tested.key? key
          message.rest.spec_describe subject
        end
      end
    end
  end

end
