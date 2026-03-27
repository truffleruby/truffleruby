# Copyright (c) 2026 TruffleRuby contributors.
# Copyright (c) 2024-2025 Oracle and/or its affiliates.
# This code is released under a tri EPL/GPL/LGPL license.
# You can use it, redistribute it and/or modify it under the terms of the:
#
# Eclipse Public License version 2.0, or
# GNU General Public License version 2, or
# GNU Lesser General Public License version 2.1.

require_relative '../ruby/spec_helper'

# Similar MRI tests are in test/mri/tests/ruby/test_optimization.rb and spec/ruby/core/warning/performance_warning_spec.rb
describe "Redefining optimized core methods" do
  it "emits performance warning for redefining core classes methods" do
    # Only Integer, Float, NilClass and Symbol classes have optimised methods.
    # They are listed in org.truffleruby.core.inlined.CoreMethodAssumptions.

    prolog = "Warning[:performance] = true"

    code_for_integer_class = <<~CODE
      class Integer
        ORIG_METHOD_PLUS = instance_method(:+)
        def +(...)
          ORIG_METHOD_PLUS.bind(self).call(...)
        end

        ORIG_METHOD_MINUS = instance_method(:-)
        def -(...)
          ORIG_METHOD_MINUS.bind(self).call(...)
        end

        ORIG_METHOD_UMINUS = instance_method(:-@)
        def -@(...)
          ORIG_METHOD_UMINUS.bind(self).call(...)
        end

        ORIG_METHOD_MULT = instance_method(:*)
        def *(...)
          ORIG_METHOD_MULT.bind(self).call(...)
        end

        ORIG_METHOD_DIVISION = instance_method(:/)
        def /(...)
          ORIG_METHOD_DIVISION.bind(self).call(...)
        end

        ORIG_METHOD_MODULO = instance_method(:%)
        def %(...)
          ORIG_METHOD_MODULO.bind(self).call(...)
        end

        ORIG_METHOD_COMPARE = instance_method(:<=>)
        def <=>(...)
          ORIG_METHOD_COMPARE.bind(self).call(...)
        end

        ORIG_METHOD_SHIFTRIGHT = instance_method(:>>)
        def >>(...)
          ORIG_METHOD_SHIFTRIGHT.bind(self).call(...)
        end

        ORIG_METHOD_SHIFTLEFTL = instance_method(:<<)
        def <<(...)
          ORIG_METHOD_SHIFTLEFTL.bind(self).call(...)
        end

        ORIG_METHOD_OR = instance_method(:|)
        def |(...)
          ORIG_METHOD_OR.bind(self).call(...)
        end

        ORIG_METHOD_AND = instance_method(:&)
        def &(...)
          ORIG_METHOD_AND.bind(self).call(...)
        end

        ORIG_METHOD_CASECOMPARE = instance_method(:===)
        def ===(...)
          ORIG_METHOD_CASECOMPARE.bind(self).call(...)
        end

        ORIG_METHOD_EQUAL = instance_method(:==)
        def ==(...)
          ORIG_METHOD_EQUAL.bind(self).call(...)
        end

        ORIG_METHOD_LT = instance_method(:<)
        def <(...)
          ORIG_METHOD_LT.bind(self).call(...)
        end

        ORIG_METHOD_LTE = instance_method(:<=)
        def <=(...)
          ORIG_METHOD_LTE.bind(self).call(...)
        end

        ORIG_METHOD_GT = instance_method(:>)
        def >(...)
          ORIG_METHOD_GT.bind(self).call(...)
        end

        ORIG_METHOD_GTE = instance_method(:>=)
        def >=(...)
          ORIG_METHOD_GTE.bind(self).call(...)
        end
      end
    CODE

    code_for_float_class = <<~CODE
      class Float
        ORIG_METHOD_PLUS = instance_method(:+)
        def +(...)
          ORIG_METHOD_PLUS.bind(self).call(...)
        end

        ORIG_METHOD_MINUS = instance_method(:-)
        def -(...)
          ORIG_METHOD_MINUS.bind(self).call(...)
        end

        ORIG_METHOD_UMINUS = instance_method(:-@)
        def -@(...)
          ORIG_METHOD_UMINUS.bind(self).call(...)
        end

        ORIG_METHOD_MULT = instance_method(:*)
        def *(...)
          ORIG_METHOD_MULT.bind(self).call(...)
        end

        ORIG_METHOD_DIVISION = instance_method(:/)
        def /(...)
          ORIG_METHOD_DIVISION.bind(self).call(...)
        end

        ORIG_METHOD_MODULO = instance_method(:%)
        def %(...)
          ORIG_METHOD_MODULO.bind(self).call(...)
        end

        ORIG_METHOD_COMPARE = instance_method(:<=>)
        def <=>(...)
          ORIG_METHOD_COMPARE.bind(self).call(...)
        end

        ORIG_METHOD_EQUAL = instance_method(:==)
        def ==(...)
          ORIG_METHOD_EQUAL.bind(self).call(...)
        end

        ORIG_METHOD_LT = instance_method(:<)
        def <(...)
          ORIG_METHOD_LT.bind(self).call(...)
        end

        ORIG_METHOD_LTE = instance_method(:<=)
        def <=(...)
          ORIG_METHOD_LTE.bind(self).call(...)
        end

        ORIG_METHOD_GT = instance_method(:>)
        def >(...)
          ORIG_METHOD_GT.bind(self).call(...)
        end

        ORIG_METHOD_GTE = instance_method(:>=)
        def >=(...)
          ORIG_METHOD_GTE.bind(self).call(...)
        end
      end
    CODE

    code_for_nil_class = <<~CODE
      class NilClass
        ORIG_METHOD_NIL = instance_method(:nil?)

        def nil?(...)
          ORIG_METHOD_NIL.bind(self).call(...)
        end
      end
    CODE

    code_for_symbol_class = <<~CODE
      class Symbol
        ORIG_METHOD_TO_PROC = instance_method(:to_proc)

        def to_proc(...)
          ORIG_METHOD_TO_PROC.bind(self).call(...)
        end
      end
    CODE

    code_for_refining = <<~CODE
    module R
      refine Integer do
        def +; end
      end
    end
    CODE

    code = [prolog, code_for_integer_class, code_for_float_class, code_for_nil_class, code_for_symbol_class, code_for_refining].join("\n")
    output = ruby_exe(code, args: "2>&1")

    output.should.include?("warning: Redefining 'Integer#+' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#-' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#-@' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#*' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#/' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#%' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#<=>' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#>>' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#<<' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#|' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#&' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#===' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#==' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#<' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#<=' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#>' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Integer#>=' disables interpreter and JIT optimizations")

    output.should.include?("warning: Redefining 'Float#+' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#-' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#-@' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#*' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#/' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#%' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#<=>' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#==' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#<' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#<=' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#>' disables interpreter and JIT optimizations")
    output.should.include?("warning: Redefining 'Float#>=' disables interpreter and JIT optimizations")

    output.should.include?("warning: Redefining 'NilClass#nil?' disables interpreter and JIT optimizations")

    output.should.include?("warning: Redefining 'Symbol#to_proc' disables interpreter and JIT optimizations")

    output.should.include?("warning: Refining 'Integer#+' disables interpreter and JIT optimizations")
  end
end

describe "Prepending a module into a class with optimised methods" do
  it "keeps the inlined nodes if no optimised method is overridden" do
    code = <<~CODE
      Warning[:performance] = true

      module M
      end

      class Integer
        prepend M
      end
    CODE

    ruby_exe(code, args: "2>&1").should == ''
  end

  it "emits performance warning if that module overrides an optimised method" do
    code = <<~CODE
      Warning[:performance] = true

      module M
        def <=>
        end
      end

      class Integer
        prepend M
      end
    CODE

    ruby_exe(code, args: "2>&1").should.include?("warning: Redefining 'Integer#<=>' disables interpreter and JIT optimizations")
  end
end
