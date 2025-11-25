# frozen_string_literal: true

module RBS
  class Validator
    attr_reader :env
    attr_reader :resolver
    attr_reader :definition_builder

    def initialize(env:, resolver: nil)
      @env = env
      @resolver = resolver
      @definition_builder = DefinitionBuilder.new(env: env)
    end

    def absolute_type(type, context:, &block)
      return type unless resolver

      type.map_type_name do |type_name, _, type|
        resolver.resolve(type_name, context: context) || (block ? yield(type) : type_name)
      end
    end

    # Validates presence of the relative type, and application arity match.
    def validate_type(type, context:)
      case type
      when Types::ClassInstance, Types::Interface, Types::Alias
        type = absolute_type(type, context: context) #: Types::ClassInstance | Types::Interface | Types::Alias

        definition_builder.validate_type_name(type.name, type.location)

        normalized_type_name = env.normalize_type_name?(type.name)

        if normalized_type_name
          type_params =
            case type
            when Types::ClassInstance
              entry = env.class_decls[normalized_type_name] or raise
              entry.type_params
            when Types::Interface
              env.interface_decls[normalized_type_name].decl.type_params
            when Types::Alias
              env.type_alias_decls[normalized_type_name].decl.type_params
            end

          InvalidTypeApplicationError.check!(
            type_name: type.name,
            args: type.args,
            params: type_params,
            location: type.location
          )
        end

      when Types::ClassSingleton
        type = absolute_type(type, context: context) #: Types::ClassSingleton
        definition_builder.validate_type_presence(type)
      end

      type.each_type do |type|
        validate_type(type, context: context)
      end
    end

    def validate_type_alias(entry:)
      type_name = entry.decl.name

      if type_alias_dependency.circular_definition?(type_name)
        location = entry.decl.location or raise
        raise RecursiveTypeAliasError.new(alias_names: [type_name], location: location)
      end

      if diagnostic = type_alias_regularity.nonregular?(type_name)
        location = entry.decl.location or raise
        raise NonregularTypeAliasError.new(diagnostic: diagnostic, location: location)
      end

      unless entry.decl.type_params.empty?
        calculator = VarianceCalculator.new(builder: definition_builder)
        result = calculator.in_type_alias(name: type_name)
        if set = result.incompatible?(entry.decl.type_params)
          set.each do |param_name|
            param = entry.decl.type_params.find {|param| param.name == param_name } or raise
            next if param.unchecked?

            raise InvalidVarianceAnnotationError.new(
              type_name: type_name,
              param: param,
              location: entry.decl.type.location
            )
          end
        end

        validate_type_params(
          entry.decl.type_params,
          type_name: type_name,
          location: entry.decl.location&.aref(:type_params)
        )
      end

      if block_given?
        yield entry.decl.type
      end
    end

    def validate_method_definition(method_def, type_name:)
      method_def.overloads.each do |overload|
        method_type = overload.method_type
        unless method_type.type_params.empty?
          loc = method_type.location&.aref(:type_params)

          validate_type_params(
            method_type.type_params,
            type_name: type_name,
            method_name: method_def.name,
            location: loc
          )
        end
      end
    end

    def validate_type_params(params, type_name: , method_name: nil, location:)
      # @type var each_node: ^() { (Symbol) -> void } -> void
      each_node = -> (&block) do
        params.each do |param|
          block[param.name]
        end
      end
      # @type var each_child: ^(Symbol) { (Symbol) -> void } -> void
      each_child = -> (name, &block) do
        if param = params.find {|p| p.name == name }
          if b = param.upper_bound_type
            b.free_variables.each do |tv|
              block[tv]
            end
          end
        end
      end

      TSort.each_strongly_connected_component(each_node, each_child) do |names|
        if names.size > 1
          params = names.map do |name|
            params.find {|param| param.name == name} or raise
          end

          raise CyclicTypeParameterBound.new(
            type_name: type_name,
            method_name: method_name,
            params: params,
            location: location
          )
        end
      end
    end

    def validate_class_alias(entry:)
      case env.normalize_module_name?(entry.decl.new_name)
      when nil
        raise NoTypeFoundError.new(type_name: entry.decl.old_name, location: entry.decl.location&.[](:old_name))
      when false
        raise CyclicClassAliasDefinitionError.new(entry)
      end

      case entry
      when Environment::ClassAliasEntry
        unless env.class_entry(entry.decl.old_name)
          raise InconsistentClassModuleAliasError.new(entry)
        end
      when Environment::ModuleAliasEntry
        unless env.module_entry(entry.decl.old_name)
          raise InconsistentClassModuleAliasError.new(entry)
        end
      end
    end

    def type_alias_dependency
      @type_alias_dependency ||= TypeAliasDependency.new(env: env)
    end

    def type_alias_regularity
      @type_alias_regularity ||= TypeAliasRegularity.validate(env: env)
    end
  end
end
