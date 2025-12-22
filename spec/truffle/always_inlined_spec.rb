# Copyright (c) 2021, 2025 Oracle and/or its affiliates. All rights reserved. This
# code is released under a tri EPL/GPL/LGPL license. You can use it,
# redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

# This spec can also run on CRuby, for comparison
describe "Always-inlined core methods" do
  describe "are part of the backtrace if the error happens inside that core method" do
    it "for #public_send" do
      -> {
        public_send()
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'public_send' }
      -> {
        public_send(Object.new)
      }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'public_send' }
    end

    it "for #respond_to?" do
      -> {
        respond_to?()
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'respond_to?' }
      -> {
        respond_to?(Object.new)
      }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'respond_to?' }
    end

    it "for #block_given?" do
      -> {
        block_given?(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'block_given?' }
      -> {
        iterator?(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'iterator?' }
    end

    it "for #binding" do
      -> {
        binding(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'binding' }
    end

    it "for #local_variables" do
      -> {
        local_variables(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'local_variables' }
    end

    it "for Class#new" do
      singleton_class = Object.new.singleton_class
      -> {
        singleton_class.new
      }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'new' }
    end

    it "for BasicObject#initialize" do
      -> {
        Object.new(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'initialize' }
    end

    it "for Kernel#dup" do
      -> {
        1.dup(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'dup' }
    end

    it "for Kernel#initialize_dup" do
      -> {
        initialize_dup()
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'initialize_dup' }
    end

    it "for Kernel#initialize_copy" do
      -> {
        initialize_copy()
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'initialize_copy' }
    end

    it "for Symbol#to_proc" do
      -> {
        :a.to_proc(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'to_proc' }
    end

    it "for BasicObject#instance_eval" do
      -> {
        BasicObject.new.instance_eval
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'instance_eval' }
    end

    it "for Module#class_eval" do
      -> {
        Module.new.class_eval
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'class_eval' }
    end

    it "for Module#module_eval" do
      -> {
        Module.new.module_eval
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'module_eval' }
    end

    it "for Module#define_method" do
      -> {
        Module.new.define_method(:wrong)
      }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'define_method' }
    end

    it "for Module#instance_method" do
      -> {
        Module.new.instance_method([])
      }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'instance_method' }
    end

    guard -> { RUBY_ENGINE != "ruby" } do
      it "for #send" do
        -> {
          send()
        }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == '__send__' }
        -> {
          send(Object.new)
        }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == '__send__' }
      end

      it "for #__send__" do
        -> {
          __send__()
        }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == '__send__' }
        -> {
          __send__(Object.new)
        }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == '__send__' }
      end

      it "for a generated attr_reader" do
        obj = Class.new { attr_reader :foo }.new
        -> {
          obj.foo(:too, :many, :args)
        }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'foo' }
      end

      it "for a generated attr_writer" do
        obj = Class.new do
          attr_writer :foo
          alias_method :writer, :foo= # so it can be called without send and a different number of arguments
        end.new
        -> {
          obj.send(:foo=, :too, :many, :args)
        }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'foo=' }
        -> {
          obj.writer(:too, :many, :args)
        }.should raise_error(ArgumentError) { |e| e.backtrace_locations[0].label.should == 'foo=' }

        obj.freeze
        -> {
          obj.foo = 42
        }.should raise_error(FrozenError) { |e| e.backtrace_locations[0].label.should == 'foo=' }
      end
    end

    it "for Module#module_function" do
      module_function = Module.instance_method(:module_function)
      Class.new do
        -> {
          module_function.bind_call(self)
        }.should raise_error(TypeError, 'module_function must be called for modules') { |e|
          e.backtrace_locations[0].label.should == 'module_function'
        }
      end
    end

    it "for main.using" do
      -> do
        eval('using "foo"', TOPLEVEL_BINDING)
      end.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'using' }
    end
  end

  describe "are not part of the backtrace if the error happens in a different method" do
    it "for #public_send" do
      -> {
        public_send(:yield_self) { raise "foo" }
      }.should raise_error(RuntimeError, "foo") { |e|
        e.backtrace_locations[0].label.should.start_with?('block (5 levels)')
        e.backtrace_locations[1].label.should == 'yield_self'
        if RUBY_ENGINE == 'ruby'
          e.backtrace_locations[2].label.should == 'public_send'
        else
          e.backtrace_locations[2].label.should.start_with?('block (4 levels)')
        end
      }
    end

    it "for #send" do
      -> {
        send(:yield_self) { raise "foo" }
      }.should raise_error(RuntimeError, "foo") { |e|
        e.backtrace_locations[0].label.should.start_with?('block (5 levels)')
        e.backtrace_locations[1].label.should == 'yield_self'
        e.backtrace_locations[2].label.should.start_with?('block (4 levels)')
      }
    end

    it "for #__send__" do
      -> {
        __send__(:yield_self) { raise "foo" }
      }.should raise_error(RuntimeError, "foo") { |e|
        e.backtrace_locations[0].label.should.start_with?('block (5 levels)')
        e.backtrace_locations[1].label.should == 'yield_self'
        e.backtrace_locations[2].label.should.start_with?('block (4 levels)')
      }
    end

    it "for Proc#call" do
      line = 0
      -> {
        line = __LINE__
        proc = -> { raise "foo" }
        proc.call
      }.should raise_error(RuntimeError, "foo") { |e|
        e.backtrace_locations[0].label.should.start_with?('block (5 levels)')
        e.backtrace_locations[0].lineno.should == line + 1
        e.backtrace_locations[1].label.should.start_with?('block (4 levels)')
        e.backtrace_locations[1].lineno.should == line + 2
      }
    end

    it "for #eval" do
      -> {
        eval(Object.new)
      }.should raise_error(TypeError) { |e|
        e.backtrace_locations[0].label.should == 'convert_type'
      }
    end

    it "for Module#class_eval with Object" do
      -> {
        Module.new.class_eval(Object.new)
      }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'convert_type' }
    end

    it "for Module#module_eval with Object" do
      -> {
        Module.new.module_eval(Object.new)
      }.should raise_error(TypeError) { |e| e.backtrace_locations[0].label.should == 'convert_type' }
    end

    guard -> { RUBY_ENGINE != "ruby" } do
      it "for #respond_to?" do
        obj = Object.new
        def obj.respond_to_missing?(name, priv)
          name == :foo ? raise("foo") : super
        end
        -> {
          obj.respond_to?(:foo)
        }.should raise_error(RuntimeError, "foo") { |e|
          e.backtrace_locations[0].label.should == 'respond_to_missing?'
          e.backtrace_locations[1].label.should.start_with?('block (5 levels)')
        }
      end

      it "for Method#call" do
        def method_to_call
          raise "foo"
        end
        line = __LINE__
        meth = method(:method_to_call)
        -> {
          meth.call
        }.should raise_error(RuntimeError, "foo") { |e|
          e.backtrace_locations[0].label.should == 'method_to_call'
          e.backtrace_locations[0].lineno.should == line - 2
          e.backtrace_locations[1].label.should.start_with?('block (5 levels)')
          e.backtrace_locations[1].lineno.should == line + 3
        }
      end

      it "for Class#new" do
        -> {
          Object.new(:wrong)
        }.should raise_error(ArgumentError) { |e|
          e.backtrace_locations[0].label.should == 'initialize'
          e.backtrace_locations[1].label.should != 'new'
          e.backtrace_locations[1].label.should.start_with?('block (5 levels)')
        }
      end

      it "for Kernel#dup" do
        klass = Class.new do
          def initialize_dup(from)
            raise ArgumentError
          end
        end
        obj = klass.new
        -> {
          obj.dup
        }.should raise_error(ArgumentError) { |e|
          e.backtrace_locations[0].label.should == 'initialize_dup'
          e.backtrace_locations[1].label.should != 'dup'
          e.backtrace_locations[1].label.should.start_with?('block (5 levels)')
        }
      end

      it "for Kernel#initialize_dup" do
        klass = Class.new do
          def initialize_copy(from)
            raise ArgumentError
          end
        end
        obj = klass.new
        -> {
          obj.dup
        }.should raise_error(ArgumentError) { |e|
          e.backtrace_locations[0].label.should == 'initialize_copy'
          e.backtrace_locations[1].label.should != 'initialize_dup'
          e.backtrace_locations[1].label.should.start_with?('block (5 levels)')
        }
      end
    end
  end

  it "go uncached if seeing too many different always-inlined methods at a call site" do
    names = (1..10).map { |i| :"attr#{i}" }
    obj = Class.new { attr_reader(*names) }.new
    names.each { |name| obj.send(name).should == nil }
  end

  it "work with each(&method(:always_inlined_method))" do
    obj = Class.new do
      [:foo].each(&method(:attr_accessor))
    end.new
    obj.foo = 42
    obj.foo.should == 42
  end
end
