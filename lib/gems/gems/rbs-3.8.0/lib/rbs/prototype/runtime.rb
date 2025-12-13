# frozen_string_literal: true

require_relative 'runtime/helpers'
require_relative 'runtime/value_object_generator'
require_relative 'runtime/reflection'

module RBS
  module Prototype
    class Runtime
      class Todo
        def initialize(builder:)
          @builder = builder
        end

        def skip_mixin?(type_name:, module_name:, mixin_class:)
          return false unless @builder.env.module_class_entry(type_name.absolute!)
          return false unless @builder.env.module_class_entry(module_name.absolute!)

          mixin_decls(type_name).any? do |decl|
            decl.instance_of?(mixin_class) && decl.name == module_name.absolute!
          end
        end

        def skip_singleton_method?(module_name:, method:, accessibility:)
          return false unless @builder.env.module_class_entry(module_name.absolute!)

          method_definition = @builder.build_singleton(module_name.absolute!).methods[method.name]
          return false unless method_definition

          method_definition.accessibility == accessibility
        end

        def skip_instance_method?(module_name:, method:, accessibility:)
          return false unless @builder.env.module_class_entry(module_name.absolute!)

          method_definition = @builder.build_instance(module_name.absolute!).methods[method.name]
          return false unless method_definition

          method_definition.accessibility == accessibility
        end

        def skip_constant?(module_name:, name:)
          namespace = Namespace.new(path: module_name.split('::').map(&:to_sym), absolute: true)
          @builder.env.constant_decl?(TypeName.new(namespace: namespace, name: name))
        end

        private

        def mixin_decls(type_name)
          type_name_absolute = type_name.absolute!
          @mixin_decls_cache ||= {} #: Hash[TypeName, Array[AST::Members::Mixin]]
          @mixin_decls_cache.fetch(type_name_absolute) do
            @mixin_decls_cache[type_name_absolute] = @builder.env.class_decls[type_name_absolute].decls.flat_map do |d|
              d.decl.members.select { |m| m.kind_of?(AST::Members::Mixin) }
            end
          end
        end
      end
      private_constant :Todo

      include Prototype::Helpers
      include Runtime::Helpers

      attr_reader :patterns
      attr_reader :env
      attr_reader :merge
      attr_reader :todo
      attr_reader :owners_included
      attr_accessor :outline

      def initialize(patterns:, env:, merge:, todo: false, owners_included: [])
        @patterns = patterns
        @decls = nil
        @modules = {}
        @env = env
        @merge = merge
        @owners_included = owners_included.map do |name|
          Object.const_get(name)
        end
        @outline = false
        @todo = todo
      end

      def target?(const)
        name = const_name(const)
        return false unless name

        patterns.any? do |pattern|
          if pattern.end_with?("*")
            (name || "").start_with?(pattern.chop)
          else
            name == pattern
          end
        end
      end

      def todo_object
        @todo_object ||= Todo.new(builder: builder) if todo
      end

      def builder
        @builder ||= DefinitionBuilder.new(env: env)
      end

      def parse(file)
        require file
      end

      def decls
        unless @decls
          @decls = []
          @modules = ObjectSpace.each_object(Module)
            .map { |mod| [const_name(mod), mod] }
            .select { |name, _| name }
            .to_h
          @modules.select { |name, mod| target?(mod) }.sort_by { |name, _| name }.each do |_, mod|
            case mod
            when Class
              generate_class mod
            when Module
              generate_module mod
            end
          end
        end

        @decls or raise
      end

      private def each_mixined_module(type_name, mod)
        each_mixined_module_one(type_name, mod) do |module_name, module_full_name, is_prepend|
          yield module_name, module_full_name, is_prepend ? AST::Members::Prepend : AST::Members::Include
        end
        each_mixined_module_one(type_name, mod.singleton_class) do |module_name, module_full_name, _|
          yield module_name, module_full_name, AST::Members::Extend
        end
      end

      private def each_mixined_module_one(type_name, mod)
        supers = Set[] #: Set[Module]
        prepends = mod.ancestors.take_while { |m| !mod.equal?(m) }.to_set

        mod.included_modules.each do |mix|
          supers.merge(mix.included_modules)
        end

        if mod.is_a?(Class)
          if superclass = mod.superclass
            superclass.included_modules.each do |mix|
              supers << mix
              supers.merge(mix.included_modules)
            end
          end
        end

        mod.included_modules.uniq.each do |mix|
          if !supers.include?(mix) || prepends.include?(mix)
            unless const_name(mix)
              RBS.logger.warn("Skipping anonymous module #{mix} included in #{mod}")
            else
              module_name = module_full_name = to_type_name(const_name!(mix), full_name: true)
              if module_full_name.namespace == type_name.namespace
                module_name = TypeName.new(name: module_full_name.name, namespace: Namespace.empty)
              end

              yield module_name, module_full_name, prepends.include?(mix)
            end
          end
        end
      end

      def method_type(method)
        untyped = Types::Bases::Any.new(location: nil)

        required_positionals = [] #: Array[Types::Function::Param]
        optional_positionals = [] #: Array[Types::Function::Param]
        rest = nil #: Types::Function::Param?
        trailing_positionals = [] #: Array[Types::Function::Param]
        required_keywords = {} #: Hash[Symbol, Types::Function::Param]
        optional_keywords = {} #: Hash[Symbol, Types::Function::Param]
        rest_keywords = nil #: Types::Function::Param?

        requireds = required_positionals

        block = nil #: Types::Block?

        method.parameters.each do |(kind, name)|
          case kind
          when :req
            requireds << Types::Function::Param.new(name: name, type: untyped)
          when :opt
            requireds = trailing_positionals
            optional_positionals << Types::Function::Param.new(name: name, type: untyped)
          when :rest
            requireds = trailing_positionals
            name = nil if name == :* # For `def f(...) end` syntax
            rest = Types::Function::Param.new(name: name, type: untyped)
          when :keyreq
            name or raise
            required_keywords[name] = Types::Function::Param.new(name: nil, type: untyped)
          when :key
            name or raise
            optional_keywords[name] = Types::Function::Param.new(name: nil, type: untyped)
          when :keyrest
            rest_keywords = Types::Function::Param.new(name: nil, type: untyped)
          when :block
            block = Types::Block.new(
              type: Types::Function.empty(untyped).update(rest_positionals: Types::Function::Param.new(name: nil, type: untyped)),
              required: true,
              self_type: nil
            )
          end
        end

        block ||= block_from_ast_of(method)

        return_type = if method.name == :initialize
                        Types::Bases::Void.new(location: nil)
                      else
                        untyped
                      end
        method_type = Types::Function.new(
          required_positionals: required_positionals,
          optional_positionals: optional_positionals,
          rest_positionals: rest,
          trailing_positionals: trailing_positionals,
          required_keywords: required_keywords,
          optional_keywords: optional_keywords,
          rest_keywords: rest_keywords,
          return_type: return_type,
        )

        MethodType.new(
          location: nil,
          type_params: [],
          type: method_type,
          block: block
        )
      end

      def merge_rbs(module_name, members, instance: nil, singleton: nil)
        if merge
          if env.class_decls[module_name.absolute!]
            # @type var kind: AST::Members::MethodDefinition::kind
            case
            when instance
              method = builder.build_instance(module_name.absolute!).methods[instance]
              method_name = instance
              kind = :instance
            when singleton
              method = builder.build_singleton(module_name.absolute!).methods[singleton]
              method_name = singleton
              kind = :singleton
            end

            if method
              members << AST::Members::MethodDefinition.new(
                name: method_name,
                overloads: method.method_types.map {|type|
                  AST::Members::MethodDefinition::Overload.new(
                    annotations: [],
                    method_type: type.update.tap do |ty|
                      def ty.to_s
                        location or raise
                        location.source
                      end
                    end
                  )
                },
                kind: kind,
                location: nil,
                comment: method.comments[0],
                annotations: method.annotations,
                overloading: false,
                visibility: nil
              )
              return
            end
          end

          yield
        else
          yield
        end
      end

      def target_method?(mod, instance: nil, singleton: nil)
        case
        when instance
          method = mod.instance_method(instance)
          method.owner == mod || owners_included.any? {|m| method.owner == m }
        when singleton
          method = mod.singleton_class.instance_method(singleton)
          method.owner == mod.singleton_class || owners_included.any? {|m| method.owner == m.singleton_class }
        else
          raise
        end
      end

      def generate_methods(mod, module_name, members)
        module_name_absolute = to_type_name(const_name!(mod), full_name: true).absolute!
        mod.singleton_methods.select {|name| target_method?(mod, singleton: name) }.sort.each do |name|
          method = mod.singleton_class.instance_method(name)
          next if todo_object&.skip_singleton_method?(module_name: module_name_absolute, method: method, accessibility: :public)

          if can_alias?(mod.singleton_class, method)
            members << AST::Members::Alias.new(
              new_name: method.name,
              old_name: method.original_name,
              kind: :singleton,
              location: nil,
              comment: nil,
              annotations: [],
            )
          else
            merge_rbs(module_name, members, singleton: name) do
              RBS.logger.info "missing #{module_name}.#{name} #{method.source_location}"

              members << AST::Members::MethodDefinition.new(
                name: method.name,
                overloads: [
                  AST::Members::MethodDefinition::Overload.new(annotations: [], method_type: method_type(method))
                ],
                kind: :singleton,
                location: nil,
                comment: nil,
                annotations: [],
                overloading: false,
                visibility: nil
              )
            end
          end
        end

        public_instance_methods = mod.public_instance_methods.select {|name| target_method?(mod, instance: name) }
        unless public_instance_methods.empty?
          public_instance_methods.sort.each do |name|
            method = mod.instance_method(name)
            next if todo_object&.skip_instance_method?(module_name: module_name_absolute, method: method, accessibility: :public)

            if can_alias?(mod, method)
              members << AST::Members::Alias.new(
                new_name: method.name,
                old_name: method.original_name,
                kind: :instance,
                location: nil,
                comment: nil,
                annotations: [],
              )
            else
              merge_rbs(module_name, members, instance: name) do
                RBS.logger.info "missing #{module_name}##{name} #{method.source_location}"

                members << AST::Members::MethodDefinition.new(
                  name: method.name,
                  overloads: [
                    AST::Members::MethodDefinition::Overload.new(annotations: [], method_type: method_type(method))
                  ],
                  kind: :instance,
                  location: nil,
                  comment: nil,
                  annotations: [],
                  overloading: false,
                  visibility: nil
                )
              end
            end
          end
        end

        private_instance_methods = mod.private_instance_methods.select {|name| target_method?(mod, instance: name) }
        unless private_instance_methods.empty?
          added = false
          members << AST::Members::Private.new(location: nil)

          private_instance_methods.sort.each do |name|
            method = mod.instance_method(name)
            next if todo_object&.skip_instance_method?(module_name: module_name_absolute, method: method, accessibility: :private)

            added = true
            if can_alias?(mod, method)
              members << AST::Members::Alias.new(
                new_name: method.name,
                old_name: method.original_name,
                kind: :instance,
                location: nil,
                comment: nil,
                annotations: [],
              )
            else
              merge_rbs(module_name, members, instance: name) do
                RBS.logger.info "missing #{module_name}##{name} #{method.source_location}"

                members << AST::Members::MethodDefinition.new(
                  name: method.name,
                  overloads: [
                    AST::Members::MethodDefinition::Overload.new(annotations: [], method_type: method_type(method))
                  ],
                  kind: :instance,
                  location: nil,
                  comment: nil,
                  annotations: [],
                  overloading: false,
                  visibility: nil
                )
              end
            end
          end

          members.pop unless added
        end
      end

      private def can_alias?(mod, method)
        return false if method.name == method.original_name

        begin
          mod.instance_method(method.original_name) && true
        rescue NameError
          false
        end
      end

      def generate_constants(mod, decls)
        module_name = const_name!(mod)
        Reflection.constants_of(mod, false).sort.each do |name|
          next if todo_object&.skip_constant?(module_name: module_name, name: name)

          begin
            value = mod.const_get(name)
          rescue StandardError, LoadError => e
            RBS.logger.warn("Skipping constant #{name} of #{mod} since #{e}")
            next
          end

          next if Reflection.object_class(value).equal?(Class)
          next if Reflection.object_class(value).equal?(Module)

          unless Reflection.object_class(value).name
            RBS.logger.warn("Skipping constant #{name} #{value} of #{mod} as an instance of anonymous class")
            next
          end

          type = case value
                 when true, false
                   Types::Bases::Bool.new(location: nil)
                 when nil
                   Types::Optional.new(
                     type: Types::Bases::Any.new(location: nil),
                     location: nil
                   )
                 when ARGF
                   Types::ClassInstance.new(name: TypeName.parse("::RBS::Unnamed::ARGFClass"), args: [], location: nil)
                 when ENV
                   Types::ClassInstance.new(name: TypeName.parse("::RBS::Unnamed::ENVClass"), args: [], location: nil)
                 else
                   value_type_name = to_type_name(const_name!(Reflection.object_class(value)), full_name: true).absolute!
                   args = type_args(value_type_name)
                   Types::ClassInstance.new(name: value_type_name, args: args, location: nil)
                 end

          decls << AST::Declarations::Constant.new(
            name: to_type_name(name.to_s),
            type: type,
            location: nil,
            comment: nil
          )
        end
      end

      def generate_super_class(mod)
        superclass = mod.superclass

        if superclass.nil? || superclass == ::Object
          nil
        elsif const_name(superclass).nil?
          RBS.logger.warn("Skipping anonymous superclass #{superclass} of #{mod}")
          nil
        else
          super_name = to_type_name(const_name!(superclass), full_name: true).absolute!
          super_args = type_args(super_name)
          AST::Declarations::Class::Super.new(name: super_name, args: super_args, location: nil)
        end
      end

      def generate_class(mod)
        type_name_absolute = to_type_name(const_name!(mod), full_name: true).absolute!
        type_name = to_type_name(const_name!(mod))
        outer_decls = ensure_outer_module_declarations(mod)

        # Check if a declaration exists for the actual module
        decl = outer_decls.detect do |decl|
          decl.is_a?(AST::Declarations::Class) && decl.name.name == only_name(mod).to_sym
        end #: AST::Declarations::Class?

        unless decl
          if StructGenerator.generatable?(mod)
            decl = StructGenerator.new(mod).build_decl
          elsif DataGenerator.generatable?(mod)
            decl = DataGenerator.new(mod).build_decl
          else
            decl = AST::Declarations::Class.new(
              name: to_type_name(only_name(mod)),
              type_params: type_params(mod),
              super_class: generate_super_class(mod),
              members: [],
              annotations: [],
              location: nil,
              comment: nil
            )
          end

          outer_decls << decl
        end

        generate_mixin(mod, decl, type_name, type_name_absolute)

        unless mod < Struct || (RUBY_VERSION >= '3.2' && mod < Data)
          generate_methods(mod, type_name, decl.members) unless outline
        end

        generate_constants mod, decl.members
      end

      def generate_module(mod)
        name = const_name(mod)

        unless name
          RBS.logger.warn("Skipping anonymous module #{mod}")
          return
        end

        type_name_absolute = to_type_name(name, full_name: true).absolute!
        type_name = to_type_name(name)
        outer_decls = ensure_outer_module_declarations(mod)

        # Check if a declaration exists for the actual class
        decl = outer_decls.detect do |decl|
          decl.is_a?(AST::Declarations::Module) && decl.name.name == only_name(mod).to_sym
        end #: AST::Declarations::Module?

        unless decl
          decl = AST::Declarations::Module.new(
            name: to_type_name(only_name(mod)),
            type_params: type_params(mod),
            self_types: [],
            members: [],
            annotations: [],
            location: nil,
            comment: nil
          )

          outer_decls << decl
        end

        generate_mixin(mod, decl, type_name, type_name_absolute)

        generate_methods(mod, type_name, decl.members) unless outline

        generate_constants mod, decl.members
      end

      def generate_mixin(mod, decl, type_name, type_name_absolute)
        each_mixined_module(type_name, mod) do |module_name, module_full_name, mixin_class|
          next if todo_object&.skip_mixin?(type_name: type_name_absolute, module_name: module_full_name, mixin_class: mixin_class)

          args = type_args(module_full_name)
          decl.members << mixin_class.new(
            name: module_name,
            args: args,
            location: nil,
            comment: nil,
            annotations: []
          )
        end
      end

      # Generate/find outer module declarations
      # This is broken down into another method to comply with `DRY`
      # This generates/finds declarations in nested form & returns the last array of declarations
      def ensure_outer_module_declarations(mod)
        # @type var outer_module_names: Array[String]
        *outer_module_names, _ = const_name!(mod).split(/::/) #=> parent = [A, B], mod = C
        # Copy the entries in ivar @decls, not .dup
        destination = @decls || [] #: Array[AST::Declarations::Class::member]

        outer_module_names&.each_with_index do |outer_module_name, i|
          current_name = outer_module_names.take(i+1).join('::')
          outer_module = @modules[current_name]
          outer_decl = destination.detect do |decl|
            case outer_module
            when Class
              decl.is_a?(AST::Declarations::Class) && decl.name.name == outer_module_name.to_sym
            when Module
              decl.is_a?(AST::Declarations::Module) && decl.name.name == outer_module_name.to_sym
            end
          end #: AST::Declarations::Class | AST::Declarations::Module | nil

          # Insert AST::Declarations if declarations are not added previously
          unless outer_decl
            outer_module or raise

            if outer_module.is_a?(Class)
              outer_decl = AST::Declarations::Class.new(
                name: to_type_name(outer_module_name),
                type_params: type_params(outer_module),
                super_class: generate_super_class(outer_module),
                members: [],
                annotations: [],
                location: nil,
                comment: nil
              )
            else
              outer_decl = AST::Declarations::Module.new(
                name: to_type_name(outer_module_name),
                type_params: type_params(outer_module),
                self_types: [],
                members: [],
                annotations: [],
                location: nil,
                comment: nil
              )
            end

            destination << outer_decl
          end

          destination = outer_decl.members
        end

        # Return the array of declarations checked out at the end
        destination
      end

      def type_args(type_name)
        if class_decl = env.class_decls.fetch(type_name.absolute!, nil)
          class_decl.type_params.size.times.map { Types::Bases::Any.new(location: nil) }
        else
          []
        end
      end

      def type_params(mod)
        type_name = to_type_name(const_name!(mod), full_name: true)
        if class_decl = env.class_decls[type_name.absolute!]
          class_decl.type_params
        else
          []
        end
      end

      def block_from_ast_of(method)
        begin
          ast = RubyVM::AbstractSyntaxTree.of(method)
        rescue ArgumentError
          return # When the method is defined in eval
        rescue RuntimeError => error
          raise unless error.message.include?("prism")
          return # When the method was compiled by prism
        end

        if ast && ast.type == :SCOPE
          block_from_body(ast)
        end
      end
    end
  end
end
