# frozen_string_literal: true

module RBS
  module AST
    class TypeParam
      attr_reader :name, :variance, :location, :upper_bound_type, :default_type

      def initialize(name:, variance:, upper_bound:, location:, default_type: nil)
        @name = name
        @variance = variance
        @upper_bound_type = upper_bound
        @location = location
        @unchecked = false
        @default_type = default_type
      end

      def upper_bound
        case upper_bound_type
        when Types::ClassInstance, Types::ClassSingleton, Types::Interface
          upper_bound_type
        end
      end

      def unchecked!(value = true)
        @unchecked = value ? true : false
        self
      end

      def unchecked?
        @unchecked
      end

      def ==(other)
        other.is_a?(TypeParam) &&
          other.name == name &&
          other.variance == variance &&
          other.upper_bound_type == upper_bound_type &&
          other.default_type == default_type &&
          other.unchecked? == unchecked?
      end

      alias eql? ==

      def hash
        self.class.hash ^ name.hash ^ variance.hash ^ upper_bound_type.hash ^ unchecked?.hash ^ default_type.hash
      end

      def to_json(state = JSON::State.new)
        {
          name: name,
          variance: variance,
          unchecked: unchecked?,
          location: location,
          upper_bound: upper_bound_type,
          default_type: default_type
        }.to_json(state)
      end

      def rename(name)
        TypeParam.new(
          name: name,
          variance: variance,
          upper_bound: upper_bound_type,
          location: location,
          default_type: default_type
        ).unchecked!(unchecked?)
      end

      def map_type(&block)
        if b = upper_bound_type
          _upper_bound_type = yield(b)
        end

        if dt = default_type
          _default_type = yield(dt)
        end

        TypeParam.new(
          name: name,
          variance: variance,
          upper_bound: _upper_bound_type,
          location: location,
          default_type: _default_type
        ).unchecked!(unchecked?)
      end

      def self.resolve_variables(params)
        return if params.empty?

        vars = Set.new(params.map(&:name))

        params.map! do |param|
          param.map_type {|bound| _ = subst_var(vars, bound) }
        end
      end

      def self.subst_var(vars, type)
        case type
        when Types::ClassInstance
          namespace = type.name.namespace
          if namespace.relative? && namespace.empty? && vars.member?(type.name.name)
            return Types::Variable.new(name: type.name.name, location: type.location)
          end
        end

        type.map_type {|t| subst_var(vars, t) }
      end

      def self.rename(params, new_names:)
        raise unless params.size == new_names.size

        subst = Substitution.build(new_names, Types::Variable.build(new_names))

        params.map.with_index do |param, index|
          new_name = new_names[index]

          TypeParam.new(
            name: new_name,
            variance: param.variance,
            upper_bound: param.upper_bound_type&.map_type {|type| type.sub(subst) },
            location: param.location,
            default_type: param.default_type&.map_type {|type| type.sub(subst) }
          ).unchecked!(param.unchecked?)
        end
      end

      def to_s
        s = +""

        if unchecked?
          s << "unchecked "
        end

        case variance
        when :invariant
          # nop
        when :covariant
          s << "out "
        when :contravariant
          s << "in "
        end

        s << name.to_s

        if type = upper_bound_type
          s << " < #{type}"
        end

        if dt = default_type
          s << " = #{dt}"
        end

        s
      end

      def self.application(params, args)
        if params.empty?
          return nil
        end

        optional_params, required_params = params.partition {|param| param.default_type }

        param_subst = Substitution.new()
        app_subst = Substitution.new()

        required_params.zip(args.take(required_params.size)).each do |param, arg|
          arg ||= Types::Bases::Any.new(location: nil)
          param_subst.add(from: param.name, to: arg)
          app_subst.add(from: param.name, to: arg)
        end

        optional_params.each do |param|
          param_subst.add(from: param.name, to: Types::Bases::Any.new(location: nil))
        end

        optional_params.zip(args.drop(required_params.size)).each do |param, arg|
          if arg
            app_subst.add(from: param.name, to: arg)
          else
            param.default_type or raise
            app_subst.add(from: param.name, to: param.default_type.sub(param_subst))
          end
        end

        app_subst
      end

      def self.normalize_args(params, args)
        app = application(params, args) or return []

        min_count = params.count { _1.default_type.nil? }
        unless min_count <= args.size && args.size <= params.size
          return args
        end

        params.zip(args).filter_map do |param, arg|
          if arg
            arg
          else
            if param.default_type
              param.default_type.sub(app)
            else
              Types::Bases::Any.new(location: nil)
            end
          end
        end
      end

      def self.validate(type_params)
        optionals = type_params.filter {|param| param.default_type }

        optional_param_names = optionals.map(&:name).sort

        optionals.filter! do |param|
          default_type = param.default_type or raise
          optional_param_names.any? { default_type.free_variables.include?(_1) }
        end

        unless optionals.empty?
          optionals
        end
      end
    end
  end
end
