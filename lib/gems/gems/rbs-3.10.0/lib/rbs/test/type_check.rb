# frozen_string_literal: true

module RBS
  module Test
    class TypeCheck
      attr_reader :self_class
      attr_reader :builder
      attr_reader :sample_size
      attr_reader :unchecked_classes
      attr_reader :instance_class
      attr_reader :class_class

      DEFAULT_SAMPLE_SIZE = 100

      def initialize(self_class:, builder:, sample_size:, unchecked_classes:, instance_class: Object, class_class: Module)
        @self_class = self_class
        @instance_class = instance_class
        @class_class = class_class
        @builder = builder
        @sample_size = sample_size
        @unchecked_classes = unchecked_classes.uniq
      end

      def overloaded_call(method, method_name, call, errors:)
        es = method.defs.map do |type_def|
          es = method_call(method_name, type_def.type, call, errors: [], annotations: type_def.annotations)

          if es.empty?
            return errors
          else
            es
          end
        end

        if es.size == 1
          errors.push(*es[0])
        else
          error = Errors::UnresolvedOverloadingError.new(
            klass: self_class,
            method_name: method_name,
            method_types: method.method_types
          )
          RBS.logger.warn do
            tag = Errors.method_tag(error)
            message = +"#{tag} UnresolvedOverloadingError "
            message << method.method_types.zip(es).map do |method_type, es|
              msg = +"method_type=`#{method_type}`"
              details = es.map do |e|
                "\"#{Errors.to_string(e).sub("#{tag} ", "") }\""
              end.join(', ')
              msg << " details=[#{details}]"
            end.join(', ')
            message
          end
          errors << error
        end

        errors
      end

      def method_call(method_name, method_type, call, errors:, annotations: [])
        return errors if method_type.type.is_a?(Types::UntypedFunction)

        args(method_name, method_type, method_type.type, call.method_call, errors, type_error: Errors::ArgumentTypeError, argument_error: Errors::ArgumentError)
        self.return(method_name, method_type, method_type.type, call.method_call, errors, return_error: Errors::ReturnTypeError, annotations:)

        if method_type.block
          case
          when !call.block_calls.empty?
            call.block_calls.each do |block_call|
              args(method_name, method_type, method_type.block.type, block_call, errors, type_error: Errors::BlockArgumentTypeError, argument_error: Errors::BlockArgumentError)
              self.return(method_name, method_type, method_type.block.type, block_call, errors, return_error: Errors::BlockReturnTypeError)
            end
          when !call.block_given
            # Block is not given
            if method_type.block.required
              errors << Errors::MissingBlockError.new(klass: self_class, method_name: method_name, method_type: method_type)
            end
          else
            # Block is given, but not yielded
          end
        else
          if call.block_given
            errors << Errors::UnexpectedBlockError.new(klass: self_class, method_name: method_name, method_type: method_type)
          end
        end

        errors
      end

      def args(method_name, method_type, fun, call, errors, type_error:, argument_error:)
        test = zip_args(call.arguments, fun) do |val, param|
          unless self.value(val, param.type)
            errors << type_error.new(klass: self_class,
                                     method_name: method_name,
                                     method_type: method_type,
                                     param: param,
                                     value: val)
          end
        end

        unless test
          errors << argument_error.new(klass: self_class,
                                       method_name: method_name,
                                       method_type: method_type)
        end
      end

      def return(method_name, method_type, fun, call, errors, return_error:, annotations: [])
        if call.return?
          return if Test.call(call.return_value, IS_AP, NilClass) && annotations.find { |a| a.string == "implicitly-returns-nil" }

          unless value(call.return_value, fun.return_type)
            errors << return_error.new(klass: self_class,
                                       method_name: method_name,
                                       method_type: method_type,
                                       type: fun.return_type,
                                       value: call.return_value)
          end
        end
      end

      def zip_keyword_args(hash, fun)
        fun.required_keywords.each do |name, param|
          if hash.key?(name)
            yield(hash[name], param)
          else
            return false
          end
        end

        fun.optional_keywords.each do |name, param|
          if hash.key?(name)
            yield(hash[name], param)
          end
        end

        hash.each do |name, value|
          next if fun.required_keywords.key?(name)
          next if fun.optional_keywords.key?(name)

          if fun.rest_keywords
            yield value, fun.rest_keywords
          else
            return false
          end
        end

        true
      end

      def keyword?(value)
        Hash === value && value.each_key.all?(Symbol)
      end

      def zip_args(args, fun, &block)
        return true if fun.is_a?(Types::UntypedFunction)

        case
        when args.empty?
          if fun.required_positionals.empty? && fun.trailing_positionals.empty? && fun.required_keywords.empty?
            true
          else
            false
          end
        when !fun.required_positionals.empty?
          yield_self do
            param, fun_ = fun.drop_head
            yield(args.first, param)
            zip_args(args.drop(1), fun_, &block)
          end
        when fun.has_keyword?
          yield_self do
            hash = args.last
            if keyword?(hash)
              zip_keyword_args(hash, fun, &block) &&
                zip_args(args.take(args.size - 1),
                         fun.update(required_keywords: {}, optional_keywords: {}, rest_keywords: nil),
                         &block)
            else
              fun.required_keywords.empty? &&
                zip_args(args,
                         fun.update(required_keywords: {}, optional_keywords: {}, rest_keywords: nil),
                         &block)
            end
          end
        when !fun.trailing_positionals.empty?
          yield_self do
            param, fun_ = fun.drop_tail
            yield(args.last, param)
            zip_args(args.take(args.size - 1), fun_, &block)
          end
        when !fun.optional_positionals.empty?
          yield_self do
            param, fun_ = fun.drop_head
            yield(args.first, param)
            zip_args(args.drop(1), fun_, &block)
          end
        when fun.rest_positionals
          yield_self do
            yield(args.first, fun.rest_positionals)
            zip_args(args.drop(1), fun, &block)
          end
        else
          false
        end
      end

      def each_sample(array, &block)
        if block
          if sample_size && array.size > sample_size
            if sample_size > 0
              size = array.size
              sample_size.times do
                yield array[rand(size)]
              end
            end
          else
            array.each(&block)
          end
        else
          enum_for :each_sample, array
        end
      end

      def get_class(type_name)
        Object.const_get(type_name.to_s)
      rescue NameError
        nil
      end

      def is_double?(value)
        unchecked_classes.any? { |unchecked_class| Test.call(value, IS_AP, Object.const_get(unchecked_class))}
      rescue NameError
        false
      end

      def value(val, type)
        if is_double?(val)
          RBS.logger.info("A double (#{val.inspect}) is detected!")
          return true
        end

        case type
        when Types::Bases::Any
          true
        when Types::Bases::Bool
          val.is_a?(TrueClass) || val.is_a?(FalseClass)
        when Types::Bases::Top
          true
        when Types::Bases::Bottom
          false
        when Types::Bases::Void
          true
        when Types::Bases::Self
          Test.call(val, IS_AP, self_class)
        when Types::Bases::Nil
          Test.call(val, IS_AP, ::NilClass)
        when Types::Bases::Class
          Test.call(val, IS_AP, class_class)
        when Types::Bases::Instance
          Test.call(val, IS_AP, instance_class)
        when Types::ClassInstance
          klass = get_class(type.name) or return false
          if params = builder.env.normalized_module_class_entry(type.name.absolute!)&.type_params
            args = AST::TypeParam.normalize_args(params, type.args)
            unless args == type.args
              type = Types::ClassInstance.new(name: type.name, args: args, location: type.location)
            end
          end

          case
          when klass == ::Array
            Test.call(val, IS_AP, klass) && each_sample(val).all? {|v| value(v, type.args[0]) }
          when klass == ::Hash
            Test.call(val, IS_AP, klass) && each_sample(val.keys).all? do |key|
              value(key, type.args[0]) && value(val[key], type.args[1])
            end
          when klass == ::Range
            Test.call(val, IS_AP, klass) && value(val.begin, type.args[0]) && value(val.end, type.args[0])
          when klass == ::Enumerator
            if Test.call(val, IS_AP, klass)
              case val.size
              when Float::INFINITY
                values = []
                ret = self
                val.lazy.take(10).each do |*args|
                  values << args
                  nil
                end
              when nil
                values = []
                count = 10_000

                ret = val.each do |*args|
                  count -= 1
                  values << args
                  break if count <= 0
                end

                if count == 0
                  ret = self
                end
              else
                values = []
                ret = val.each do |*args|
                  values << args
                  nil
                end
              end

              value_check = values.empty? || each_sample(values).all? do |v|
                if v.size == 1
                  # Only one block argument.
                  value(v[0], type.args[0]) || value(v, type.args[0])
                else
                  value(v, type.args[0])
                end
              end

              return_check = if ret.equal?(self)
                type.args[1].is_a?(Types::Bases::Bottom)
              else
                value(ret, type.args[1])
              end

              value_check && return_check
            end
          else
            Test.call(val, IS_AP, klass)
          end
        when Types::ClassSingleton
          klass = get_class(type.name) or return false
          singleton_class = begin
                              klass.singleton_class
                            rescue TypeError
                              return false
                            end
          val.is_a?(singleton_class)
        when Types::Interface
          if (definition = builder.build_interface(type.name.absolute!))
            definition.methods.each.all? do |method_name, method|
              next false unless Test.call(val, RESPOND_TOP, method_name)

              meth = Test.call(val, METHOD, method_name)
              method.defs.all? do |type_def|
                type_def.member.overloads.all? do |overload|
                  callable_argument?(meth.parameters, overload.method_type)
                end
              end
            end
          end
        when Types::Variable
          true
        when Types::Literal
          type.literal == val
        when Types::Union
          type.types.any? {|type| value(val, type) }
        when Types::Intersection
          type.types.all? {|type| value(val, type) }
        when Types::Optional
          Test.call(val, IS_AP, ::NilClass) || value(val, type.type)
        when Types::Alias
          value(val, builder.expand_alias2(type.name.absolute!, type.args))
        when Types::Tuple
          Test.call(val, IS_AP, ::Array) &&
            type.types.length == val.length &&
            type.types.map.with_index {|ty, index| value(val[index], ty) }.all?
        when Types::Record
          Test::call(val, IS_AP, ::Hash) &&
            type.fields.map {|key, type| value(val[key], type) }.all?
        when Types::Proc
          Test::call(val, IS_AP, ::Proc)
        else
          false
        end
      end

      private

      def callable_argument?(parameters, method_type)
        fun = method_type.type
        take_has_rest = !!parameters.find { |(op, _)| op == :rest }

        return true if fun.is_a?(Types::UntypedFunction)

        fun.required_positionals.each do
          op, _ = parameters.first
          return false if op.nil? || op == :keyreq || op == :key || op == :keyrest
          parameters.shift if op == :req || op == :opt
        end

        fun.optional_positionals.each do
          op, _ = parameters.first
          return false if op.nil? || op == :req || op == :keyreq || op == :key || op == :keyrest
          parameters.shift if op == :opt
        end

        if fun.rest_positionals
          op, _ = parameters.shift
          return false if op.nil? || op != :rest
        end

        fun.trailing_positionals.each do
          op, _ = parameters.first
          return false if !take_has_rest && (op.nil? || op == :keyreq || op == :key || op == :keyrest)
          index = parameters.find_index { |(op, _)| op == :req }
          parameters.delete_at(index) if index
        end

        if fun.has_keyword?
          return false if !take_has_rest && parameters.empty?

          fun.required_keywords.each do |name, _|
            return false if !take_has_rest && parameters.empty?
            index = parameters.find_index { |(op, n)| (op == :keyreq || op == :key) && n == name }
            parameters.delete_at(index) if index
          end

          if !fun.optional_keywords.empty?
            fun.optional_keywords.each do |name, _|
              return false if !take_has_rest && parameters.empty?
              index = parameters.find_index { |(op, n)| op == :key && n == name }
              parameters.delete_at(index) if index
            end
            op, _ = parameters.first
            return false if op == :req
          end

          if fun.rest_keywords
            op, _ = parameters.first
            return false if (!take_has_rest && op.nil?)
            # f(a) allows (Integer, a: Integer)
            return false if op == :req && fun.required_keywords.empty?
          end

          op, _ = parameters.first
          return true if (op == :req || op == :opt) && parameters.length == 1
        end

        # rest required arguments
        op, _ = parameters.first
        return false if op == :req || op == :keyreq

        true
      end
    end
  end
end
