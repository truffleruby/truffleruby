# frozen_string_literal: true

require 'rbs'
require 'rubygems'
require 'rbs/cli'
require_relative 'methods'

module ReplTypeCompletor
  module Types
    OBJECT_TO_TYPE_SAMPLE_SIZE = 50

    singleton_class.attr_reader :rbs_builder, :rbs_load_error

    def self.rbs_load_started?
      !!@load_started
    end

    def self.preload_rbs_builder
      return if rbs_load_started?
      @load_started = true
      Thread.new do
        load_rbs_builder
      end
    end

    def self.load_rbs_builder
      @load_started = true
      loader = RBS::CLI::LibraryOptions.new.loader
      sig_path = Pathname('sig')
      loader.add path: sig_path
      expanded_sig_path = sig_path.expand_path.to_s

      unless File.exist?('rbs_collection.yaml')
        # Load rbs signature from gems. This is a fallback when rbs_collection.yaml is not available.
        Gem.loaded_specs.values.each do |spec|
          gem_sig_path = File.expand_path("#{spec.gem_dir}/sig")
          loader.add(library: spec.name, version: spec.version) if Dir.exist?(gem_sig_path) && expanded_sig_path != gem_sig_path
        end
      end

      # Hack to make this thread priority lower, not to block the main thread.
      thread_pass_counter = 0
      tracepoint = TracePoint.new(:call) do
        Thread.pass if ((thread_pass_counter += 1) % 10).zero?
      end
      tracepoint.enable do
        env = RBS::Environment.from_loader(loader)
        @rbs_builder = RBS::DefinitionBuilder.new env: env.resolve_type_names
      end
    rescue LoadError, StandardError => e
      @rbs_load_error = e
      nil
    end

    def self.class_name_of(klass)
      while true
        name = Methods::MODULE_NAME_METHOD.bind_call klass
        return name if name

        klass = klass.superclass
      end
    end

    if RBS::TypeName.respond_to?(:parse) # RBS >= 3.8.0
      def self.rbs_absolute_type_name(name)
        RBS::TypeName.parse(name).absolute!
      end
    else
      def self.rbs_absolute_type_name(name)
        # Deprecated in RBS 3.8.0
        RBS::TypeName(name).absolute!
      end
    end

    def self.rbs_search_method(klass, method_name, singleton)
      return unless rbs_builder

      klass.ancestors.each do |ancestor|
        next unless (name = Methods::MODULE_NAME_METHOD.bind_call(ancestor))

        type_name = rbs_absolute_type_name(name)
        definition = (singleton ? rbs_builder.build_singleton(type_name) : rbs_builder.build_instance(type_name)) rescue nil
        method = definition.methods[method_name] if definition
        return method if method
      end
      nil
    end

    def self.method_return_type(type, method_name)
      receivers = type.types.map do |t|
        case t
        in SingletonType
          [t, t.module_or_class, true]
        in InstanceType
          [t, t.klass, false]
        end
      end
      types = receivers.flat_map do |receiver_type, klass, singleton|
        method = rbs_search_method klass, method_name, singleton
        next [] unless method
        method.method_types.map do |method|
          from_rbs_type(method.type.return_type, receiver_type, {})
        end
      end
      UnionType[*types]
    end

    def self.accessor_method_return_type(type, method_name)
      return unless method_name.match?(/\A[a-z_][a-z_0-9]*\z/)

      ivar_name = :"@#{method_name}"
      instances = type.types.filter_map do |t|
        case t
        in SingletonType
          t.module_or_class
        in InstanceType
          t.instances
        end
      end.flatten
      instances = instances.sample(OBJECT_TO_TYPE_SAMPLE_SIZE) if instances.size > OBJECT_TO_TYPE_SAMPLE_SIZE
      objects = []
      instances.each do |instance|
        if Methods::OBJECT_INSTANCE_VARIABLE_DEFINED_METHOD.bind_call(instance, ivar_name)
          objects << Methods::OBJECT_INSTANCE_VARIABLE_GET_METHOD.bind_call(instance, ivar_name)
        end
      end
      union_type_from_objects(objects) unless objects.empty?
    end

    def self.rbs_methods(type, method_name, args_types, kwargs_type, has_block)
      return [] unless rbs_builder

      receivers = type.types.map do |t|
        case t
        in SingletonType
          [t, t.module_or_class, true]
        in InstanceType
          [t, t.klass, false]
        end
      end
      has_splat = args_types.include?(nil)
      methods_with_score = receivers.flat_map do |receiver_type, klass, singleton|
        method = rbs_search_method klass, method_name, singleton
        next [] unless method
        method.method_types.filter_map do |method_type|
          next unless method_type.type.respond_to?(:required_positionals)

          score = 0
          score += 2 if !!method_type.block == has_block
          reqs = method_type.type.required_positionals
          opts = method_type.type.optional_positionals
          rest = method_type.type.rest_positionals
          trailings = method_type.type.trailing_positionals
          keyreqs = method_type.type.required_keywords
          keyopts = method_type.type.optional_keywords
          keyrest = method_type.type.rest_keywords
          args = args_types
          if kwargs_type&.any? && keyreqs.empty? && keyopts.empty? && keyrest.nil?
            kw_value_type = UnionType[*kwargs_type.values]
            args += [InstanceType.new(Hash, K: SYMBOL, V: kw_value_type)]
          end
          if has_splat
            score += 1 if args.count(&:itself) <= reqs.size + opts.size + trailings.size
          elsif reqs.size + trailings.size <= args.size && (rest || args.size <= reqs.size + opts.size + trailings.size)
            score += 2
            centers = args[reqs.size...-trailings.size]
            given = args.first(reqs.size) + centers.take(opts.size) + args.last(trailings.size)
            expected = (reqs + opts.take(centers.size) + trailings).map(&:type)
            if rest
              given << UnionType[*centers.drop(opts.size)]
              expected << rest.type
            end
            if given.any?
              score += given.zip(expected).count do |t, e|
                e = from_rbs_type e, receiver_type
                intersect?(t, e) || (intersect?(STRING, e) && t.methods.include?(:to_str)) || (intersect?(INTEGER, e) && t.methods.include?(:to_int)) || (intersect?(ARRAY, e) && t.methods.include?(:to_ary))
              end.fdiv(given.size)
            end
          end
          [[method_type, given || [], expected || []], score]
        end
      end
      max_score = methods_with_score.map(&:last).max
      methods_with_score.select { _2 == max_score }.map(&:first)
    end

    def self.intersect?(a, b)
      atypes = a.types.group_by(&:class)
      btypes = b.types.group_by(&:class)
      if atypes[SingletonType] && btypes[SingletonType]
        aa, bb = [atypes, btypes].map {|types| types[SingletonType].map(&:module_or_class) }
        return true if (aa & bb).any?
      end

      aa, bb = [atypes, btypes].map {|types| (types[InstanceType] || []).map(&:klass) }
      (aa.flat_map(&:ancestors) & bb).any?
    end

    def self.type_from_object(object)
      case object
      when Array
        InstanceType.new Array, nil, [object]
      when Hash
        InstanceType.new Hash, nil, [object]
      when Module
        SingletonType.new object
      else
        InstanceType.new Methods::OBJECT_CLASS_METHOD.bind_call(object), nil, [object]
      end
    end

    def self.union_type_from_objects(objects)
      instances = objects.size <= OBJECT_TO_TYPE_SAMPLE_SIZE ? objects : objects.sample(OBJECT_TO_TYPE_SAMPLE_SIZE)
      modules, instances = instances.partition { Module === _1 }
      class_instances = instances.group_by { Methods::OBJECT_CLASS_METHOD.bind_call(_1) }
      UnionType[*class_instances.map { InstanceType.new _1, nil, _2 }, *modules.uniq.map { SingletonType.new _1 }]
    end

    class SingletonType
      attr_reader :module_or_class
      def initialize(module_or_class)
        @module_or_class = module_or_class
      end
      def transform() = yield(self)
      def methods() = @module_or_class.methods
      def all_methods() = methods | @module_or_class.private_methods
      def constants() = @module_or_class.constants
      def types() = [self]
      def nillable?() = false
      def nonnillable() = self
      def inspect
        "#{module_or_class}.itself"
      end
    end

    class InstanceType
      attr_reader :klass, :raw_params, :instances
      def initialize(klass, params = nil, instances = nil)
        @klass = klass
        @raw_params = params if params && !params.empty?
        @instances = instances if instances && !instances.empty?
      end

      def transform() = yield(self)
      def methods() = rbs_methods.select { _2.public? }.keys | @klass.instance_methods | singleton_methods
      def all_methods() = rbs_methods.keys | @klass.instance_methods | @klass.private_instance_methods | singleton_methods | instances_private_methods

      def singleton_methods
        return [] unless @instances
        @singleton_methods ||= @instances.map do |instance|
          Methods::OBJECT_SINGLETON_METHODS_METHOD.bind_call(instance)
        end.inject(:|)
      end

      def instances_private_methods
        return [] unless @instances
        @private_instances_methods ||= @instances.map do |instance|
          Methods::OBJECT_PRIVATE_METHODS_METHOD.bind_call(instance, false)
        end.inject(:|)
      end

      def params
        @params ||= expand_params
      end

      def expand_params
        params = @raw_params || {}
        return params unless @instances

        if @klass == Array
          type = Types.union_type_from_objects(@instances.flatten(1))
          { Elem: UnionType[*params[:Elem], *type] }
        elsif @klass == Hash
          key = Types.union_type_from_objects(@instances.map(&:keys).flatten(1))
          value = Types.union_type_from_objects(@instances.map(&:values).flatten(1))
          {
            K: UnionType[*params[:K], key],
            V: UnionType[*params[:V], value]
          }
        else
          params
        end
      end

      def constants() = []
      def types() = [self]
      def nillable?() = (@klass == NilClass)
      def nonnillable() = self

      def rbs_methods
        return {} unless Types.rbs_builder

        name = Types.class_name_of(@klass)
        type_name = Types.rbs_absolute_type_name(name)
        Types.rbs_builder.build_instance(type_name).methods rescue {}
      end

      def inspect
        if !@params && (@klass == Array || @klass == Hash) && @instances
          "#{inspect_without_params}[unresolved]"
        elsif params.empty?
          inspect_without_params
        else
          params_string = "[#{params.map { "#{_1}: #{_2.inspect}" }.join(', ')}]"
          "#{inspect_without_params}#{params_string}"
        end
      end

      def inspect_without_params
        if klass == NilClass
          'nil'
        elsif klass == TrueClass
          'true'
        elsif klass == FalseClass
          'false'
        else
          klass.to_s
        end
      end
    end

    NIL = InstanceType.new NilClass
    OBJECT = InstanceType.new Object
    TRUE = InstanceType.new TrueClass
    FALSE = InstanceType.new FalseClass
    SYMBOL = InstanceType.new Symbol
    STRING = InstanceType.new String
    INTEGER = InstanceType.new Integer
    RANGE = InstanceType.new Range
    REGEXP = InstanceType.new Regexp
    FLOAT = InstanceType.new Float
    RATIONAL = InstanceType.new Rational
    COMPLEX = InstanceType.new Complex
    ARRAY = InstanceType.new Array
    HASH = InstanceType.new Hash
    CLASS = InstanceType.new Class
    MODULE = InstanceType.new Module
    PROC = InstanceType.new Proc

    class UnionType
      attr_reader :types

      def initialize(*types)
        @types = []
        singleton_types = []
        instance_types = {}
        collect = -> type do
          case type
          in UnionType
            type.types.each(&collect)
          in InstanceType
            params, instances = (instance_types[type.klass] ||= [{}, []])
            type.instances&.each { instances << _1 }
            type.raw_params&.each do |k, v|
              (params[k] ||= []) << v
            end
          in SingletonType
            singleton_types << type
          end
        end
        types.each(&collect)
        @types = singleton_types.uniq + instance_types.map do |klass, (params, instances)|
          params = params.transform_values { |v| UnionType[*v] }
          InstanceType.new(klass, params, instances)
        end
      end

      def transform(&block)
        UnionType[*types.map(&block)]
      end

      def nillable?
        types.any?(&:nillable?)
      end

      def nonnillable
        UnionType[*types.reject { _1.is_a?(InstanceType) && _1.klass == NilClass }]
      end

      def self.[](*types)
        type = new(*types)
        if type.types.empty?
          OBJECT
        elsif type.types.size == 1
          type.types.first
        else
          type
        end
      end

      def methods() = @types.flat_map(&:methods).uniq
      def all_methods() = @types.flat_map(&:all_methods).uniq
      def constants() = @types.flat_map(&:constants).uniq
      def inspect() = @types.map(&:inspect).sort.join(' | ')
    end

    BOOLEAN = UnionType[TRUE, FALSE]

    def self.array_of(*types)
      type = types.size >= 2 ? UnionType[*types] : types.first || OBJECT
      InstanceType.new Array, Elem: type
    end

    def self.from_rbs_type(return_type, self_type, extra_vars = {})
      case return_type
      when RBS::Types::Bases::Self
        self_type
      when RBS::Types::Bases::Bottom, RBS::Types::Bases::Nil
        NIL
      when RBS::Types::Bases::Any, RBS::Types::Bases::Void
        OBJECT
      when RBS::Types::Bases::Class
        self_type.transform do |type|
          case type
          in SingletonType
            InstanceType.new(self_type.module_or_class.is_a?(Class) ? Class : Module)
          in InstanceType
            SingletonType.new type.klass
          end
        end
        UnionType[*types]
      when RBS::Types::Bases::Bool
        BOOLEAN
      when RBS::Types::Bases::Instance
        self_type.transform do |type|
          if type.is_a?(SingletonType) && type.module_or_class.is_a?(Class)
            InstanceType.new type.module_or_class
          elsif type.is_a?(InstanceType)
            InstanceType.new type.klass
          else
            OBJECT
          end
        end
      when RBS::Types::Union, RBS::Types::Intersection
        # Intersection is unsupported. fallback to union type
        UnionType[*return_type.types.map { from_rbs_type _1, self_type, extra_vars }]
      when RBS::Types::Proc
        PROC
      when RBS::Types::Tuple
        elem = UnionType[*return_type.types.map { from_rbs_type _1, self_type, extra_vars }]
        InstanceType.new Array, Elem: elem
      when RBS::Types::Record
        InstanceType.new Hash, K: SYMBOL, V: OBJECT
      when RBS::Types::Literal
        InstanceType.new return_type.literal.class
      when RBS::Types::Variable
        if extra_vars.key? return_type.name
          extra_vars[return_type.name]
        elsif self_type.is_a? InstanceType
          self_type.params[return_type.name] || OBJECT
        elsif self_type.is_a? UnionType
          types = self_type.types.filter_map do |t|
            t.params[return_type.name] if t.is_a? InstanceType
          end
          UnionType[*types]
        else
          OBJECT
        end
      when RBS::Types::Optional
        UnionType[from_rbs_type(return_type.type, self_type, extra_vars), NIL]
      when RBS::Types::Alias
        case return_type.name.name
        when :int
          INTEGER
        when :boolish
          BOOLEAN
        when :string
          STRING
        else
          # TODO: ???
          OBJECT
        end
      when RBS::Types::Interface
        # unimplemented
        OBJECT
      when RBS::Types::ClassInstance
        klass = return_type.name.to_namespace.path.reduce(Object) { _1.const_get _2 }
        if return_type.args
          args = return_type.args.map { from_rbs_type _1, self_type, extra_vars }
          names = rbs_builder.build_singleton(return_type.name).type_params
          params = names.map.with_index { [_1, args[_2] || OBJECT] }.to_h
        end
        InstanceType.new klass, params || {}
      else
        OBJECT
      end
    end

    def self.method_return_bottom?(method)
      method.type.return_type.is_a? RBS::Types::Bases::Bottom
    end

    def self.match_free_variables(vars, types, values)
      accumulator = {}
      types.zip values do |t, v|
        _match_free_variable(vars, t, v, accumulator) if v
      end
      accumulator.transform_values { UnionType[*_1] }
    end

    def self._match_free_variable(vars, rbs_type, value, accumulator)
      case [rbs_type, value]
      in [RBS::Types::Variable,]
        (accumulator[rbs_type.name] ||= []) << value if vars.include? rbs_type.name
      in [RBS::Types::ClassInstance, InstanceType]
        names = rbs_builder.build_singleton(rbs_type.name).type_params
        names.zip(rbs_type.args).each do |name, arg|
          v = value.params[name]
          _match_free_variable vars, arg, v, accumulator if v
        end
      in [RBS::Types::Tuple, InstanceType] if value.klass == Array
        v = value.params[:Elem]
        rbs_type.types.each do |t|
          _match_free_variable vars, t, v, accumulator
        end
      in [RBS::Types::Record, InstanceType] if value.klass == Hash
        # TODO
      in [RBS::Types::Interface,]
        definition = rbs_builder.build_interface rbs_type.name
        convert = {}
        definition.type_params.zip(rbs_type.args).each do |from, arg|
          convert[from] = arg.name if arg.is_a? RBS::Types::Variable
        end
        return if convert.empty?
        ac = {}
        definition.methods.each do |method_name, method|
          return_type = method_return_type value, method_name
          method.defs.each do |method_def|
            interface_return_type = method_def.type.type.return_type
            _match_free_variable convert, interface_return_type, return_type, ac
          end
        end
        convert.each do |from, to|
          values = ac[from]
          (accumulator[to] ||= []).concat values if values
        end
      else
      end
    end
  end
end
