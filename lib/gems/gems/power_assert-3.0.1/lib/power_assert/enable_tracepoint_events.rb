require 'power_assert/configuration'

if PowerAssert.configuration._redefinition
  module PowerAssert
    # set redefined flag
    basic_classes = [
      Integer, Float, String, Array, Hash, Symbol, Time, Regexp, NilClass, TrueClass, FalseClass
    ]

    basic_operators = [
      :+, :-, :*, :/, :%, :==, :===, :<, :<=, :<<, :[], :[]=, :length, :size,
      :empty?, :nil?, :succ, :>, :>=, :!, :!=, :=~, :freeze, :-@, :max, :min,
      # :call (it is just used for block call optimization)
      :&, :|,
      # :default (no specialized instruction for this)
      :pack, :include?,
    ]

    basic_classes.each do |klass|
      basic_operators.each do |bop|
        if klass.public_method_defined?(bop)
          refine(klass) do
            define_method(bop) {}
          end
        end
      end
    end

    # bypass check_cfunc
    refine BasicObject do
      def !
      end

      def ==
      end
    end

    refine Module do
      def ==
      end
    end

    refine Class do
      def new
      end
    end
  end
end

# disable optimization
RubyVM::InstructionSequence.compile_option = {
  specialized_instruction: false
}
