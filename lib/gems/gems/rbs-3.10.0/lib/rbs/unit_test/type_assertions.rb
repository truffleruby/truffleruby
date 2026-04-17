# frozen_string_literal: true
module RBS
  module UnitTest
    module TypeAssertions
      module ClassMethods
        attr_reader :target

        def library(*libs)
          if @libs
            raise "Multiple #library calls are not allowed"
          end

          @libs = libs
          @env = nil
          @target = nil
        end

        @@env_cache = {} #: Hash[Array[String], RBS::Environment]

        def env
          @env = @@env_cache[@libs] ||=
            begin
              loader = RBS::EnvironmentLoader.new
              (@libs || []).each do |lib|
                loader.add(library: lib, version: nil)
              end

              RBS::Environment.from_loader(loader).resolve_type_names
            end
        end

        def builder
          @builder ||= RBS::DefinitionBuilder.new(env: env)
        end

        def testing(type_or_string)
          type = case type_or_string
                 when String
                   RBS::Parser.parse_type(type_or_string, variables: []) || raise
                 else
                   type_or_string
                 end

          definition = case type
                       when RBS::Types::ClassInstance
                         builder.build_instance(type.name)
                       when RBS::Types::ClassSingleton
                         builder.build_singleton(type.name)
                       else
                         raise "Test target should be class instance or class singleton: #{type}"
                       end

          @target = [type, definition] #: [target_type, Definition]
        end
      end

      def self.included(base)
        base.extend ClassMethods
      end

      def env
        (_ = self.class).env
      end

      def builder
        (_ = self.class).builder
      end

      def targets
        @targets ||= []
      end

      def target
        targets.last || (_ = self.class).target
      end

      def testing(type_or_string)
        type = case type_or_string
               when String
                 RBS::Parser.parse_type(type_or_string, variables: [])
               else
                 type_or_string
               end

        definition = case type
                     when RBS::Types::ClassInstance
                       builder.build_instance(type.name)
                     when RBS::Types::ClassSingleton
                       builder.build_singleton(type.name)
                     else
                       raise "Test target should be class instance or class singleton: #{type}"
                     end

        targets.push(
          [
            type,  #: target_type
            definition
          ]
        )

        if block_given?
          begin
            yield
          ensure
            targets.pop
          end
        else
          [type, definition]
        end
      end

      def instance_class
        type, _ = target

        case type
        when RBS::Types::ClassSingleton, RBS::Types::ClassInstance
          Object.const_get(type.name.to_s)
        end
      end

      def class_class
        type, _ = target

        case type
        when RBS::Types::ClassSingleton, RBS::Types::ClassInstance
          Object.const_get(type.name.to_s).singleton_class
        end
      end

      def send_setup(method_type, receiver, method, args, proc)
        mt =
          case method_type
          when String
            RBS::Parser.parse_method_type(method_type, variables: []) || raise
          when RBS::MethodType
            method_type
          end

        validate_simple_method_type(mt)

        trace = [] #: Array[Test::CallTrace]
        spy = Spy.wrap(receiver, method)
        spy.callback = -> (result) { trace << result }

        result = nil #: untyped
        exception = nil #: Exception?
        non_jump_exit = true

        begin
          result = catch do |tag|
            @break_tag = tag
            spy.wrapped_object.__send__(method, *args, &proc)
          ensure
            @break_tag = nil
          end

          non_jump_exit = false
        rescue Exception => exn
          exception = exn
        ensure
          if non_jump_exit && !exception
            raise "`break` nor `return` from blocks given to `assert_send_type` are prohibited. Use `#break_from_block` instead."
          end
        end

        last_trace = trace.last or raise "empty trace"

        yield(mt, last_trace, result, exception)
      end

      ruby2_keywords def assert_send_type(method_type, receiver, method, *args, &block)
        send_setup(method_type, receiver, method, args, block) do |method_type, trace, result, exception|
          typecheck = RBS::Test::TypeCheck.new(
            self_class: receiver.class,
            builder: builder,
            sample_size: 100,
            unchecked_classes: [],
            instance_class: instance_class,
            class_class: class_class
          )
          errors = typecheck.method_call(method, method_type, trace, errors: [])

          assert_empty errors.map {|x| RBS::Test::Errors.to_string(x) }, "Call trace does not match with given method type: #{trace.inspect}"

          method_defs = method_defs(method)
          all_errors = method_defs.map {|t| typecheck.method_call(method, t.type, trace, errors: [], annotations: t.each_annotation.to_a) }
          assert all_errors.any? {|es| es.empty? }, "Call trace does not match one of method definitions:\n  #{trace.inspect}\n  #{method_defs.map(&:type).join(" | ")}"

          raise exception if exception

          result
        end
      end

      ruby2_keywords def refute_send_type(method_type, receiver, method, *args, &block)
        send_setup(method_type, receiver, method, args, block) do |method_type, trace, result, exception|
          method_type = method_type.update(
            block:
              if method_type.block
                RBS::Types::Block.new(
                  type: method_type.block.type.with_return_type(RBS::Types::Bases::Any.new(location: nil)),
                  required: method_type.block.required,
                  self_type: nil
                )
              end,
            type: method_type.type.with_return_type(RBS::Types::Bases::Any.new(location: nil))
          )

          typecheck = RBS::Test::TypeCheck.new(
            self_class: receiver.class,
            instance_class: instance_class,
            class_class: class_class,
            builder: builder,
            sample_size: 100,
            unchecked_classes: []
          )
          errors = typecheck.method_call(method, method_type, trace, errors: [])

          assert_operator exception, :is_a?, ::Exception
          assert_empty errors.map {|x| RBS::Test::Errors.to_string(x) }

          method_defs = method_defs(method)
          all_errors = method_defs.map {|t| typecheck.method_call(method, t.type, trace, errors: [], annotations: t.each_annotation.to_a) }
          assert all_errors.all? {|es| es.size > 0 }, "Call trace unexpectedly matches one of method definitions:\n  #{trace.inspect}\n  #{method_defs.map(&:type).join(" | ")}"

          result
        end
      end

      def method_defs(method)
        type, definition = target

        case type
        when Types::ClassInstance
          subst = RBS::Substitution.build(definition.type_params, type.args)
          definition.methods[method].defs.map do |type_def|
            type_def.update(
              type: type_def.type.sub(subst)
            )
          end
        when Types::ClassSingleton
          definition.methods[method].defs
        else
          raise
        end
      end

      def method_types(method)
        method_defs(method).map(&:type)
      end

      def allows_error(*errors)
        yield
      rescue *errors => exn
        notify "Error allowed: #{exn.inspect}"
      end

      def assert_const_type(type, constant_name)
        constant = Object.const_get(constant_name)

        typecheck = RBS::Test::TypeCheck.new(
          self_class: constant.class,
          instance_class: instance_class,
          class_class: class_class,
          builder: builder,
          sample_size: 100,
          unchecked_classes: []
        )

        value_type =
          case type
          when String
            RBS::Parser.parse_type(type, variables: []) || raise
          else
            type
          end

        assert typecheck.value(constant, value_type), "`#{constant_name}` (#{constant.inspect}) must be compatible with given type `#{value_type}`"

        type_name = TypeName.parse(constant_name).absolute!
        definition = env.constant_entry(type_name)
        assert definition, "Cannot find RBS type definition of `#{constant_name}`"

        case definition
        when RBS::Environment::ClassEntry, RBS::Environment::ModuleEntry
          definition_type = RBS::Types::ClassSingleton.new(name: type_name, location: nil)
        when RBS::Environment::ClassAliasEntry, RBS::Environment::ModuleAliasEntry
          type_name = env.normalize_type_name!(type_name)
          definition_type = RBS::Types::ClassSingleton.new(name: type_name, location: nil)
        when RBS::Environment::ConstantEntry
          definition_type = definition.decl.type
        end

        assert definition_type, "Cannot find RBS entry for `#{constant_name}`"
        definition_type or raise
        assert typecheck.value(constant, definition_type), "`#{constant_name}` (#{constant.inspect}) must be compatible with RBS type definition `#{definition_type}`"
      end

      def assert_type(type, value)
        typecheck = RBS::Test::TypeCheck.new(
          self_class: value.class,
          instance_class: _ = "No `instance` class allowed",
          class_class: _ = "No `class` class allowed",
          builder: builder,
          sample_size: 100,
          unchecked_classes: []
        )

        type =
          case type
          when String
            RBS::Parser.parse_type(type, variables: []) or raise
          else
            type
          end

        assert typecheck.value(value, type), "`#{value.inspect}` must be compatible with given type `#{type}`"
      end

      def allow_non_simple_method_type()
        begin
          @allows_non_simple_method_type = true
          yield
        rescue
          @allows_non_simple_method_type = false
        end
      end

      def validate_simple_method_type(type)
        return if @allows_non_simple_method_type

        refute_predicate type, :has_self_type?, "`self` types is prohibited in method type: `#{type}`"
        refute_predicate type, :has_classish_type?, "`instance` and `class` types is prohibited in method type: `#{type}`"
        refute_predicate type, :with_nonreturn_void?, "`void` is only allowed at return type or generics parameters: `#{type}`"
      end

      def break_from_block(value = nil)
        raise "Cannot break without `@break_tag`" unless @break_tag
        throw @break_tag, value
      end

      def pass(message = nil)
        assert true, message
      end
    end
  end
end
