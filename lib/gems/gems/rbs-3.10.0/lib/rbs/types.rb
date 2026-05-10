# frozen_string_literal: true

module RBS
  module Types
    module NoFreeVariables
      def free_variables(set = Set.new)
        set
      end
    end

    module NoSubst
      def sub(s)
        self
      end
    end

    module NoTypeName
      def map_type_name(&)
        self
      end
    end

    module EmptyEachType
      def each_type
        if block_given?
          # nop
        else
          enum_for :each_type
        end
      end

      def map_type(&block)
        if block
          _ = self
        else
          enum_for(:map_type)
        end
      end
    end

    module Bases
      class Base
        attr_reader :location

        def initialize(location:)
          @location = location
        end

        def ==(other)
          other.is_a?(self.class)
        end

        def hash
          self.class.hash
        end

        alias eql? ==

        include NoFreeVariables
        include NoSubst
        include EmptyEachType
        include NoTypeName

        def to_json(state = _ = nil)
          klass = to_s.to_sym
          { class: klass, location: location }.to_json(state)
        end

        def to_s(level = 0)
          case self
          when Types::Bases::Bool
            'bool'
          when Types::Bases::Void
            'void'
          when Types::Bases::Any
            raise
          when Types::Bases::Nil
            'nil'
          when Types::Bases::Top
            'top'
          when Types::Bases::Bottom
            'bot'
          when Types::Bases::Self
            'self'
          when Types::Bases::Instance
            'instance'
          when Types::Bases::Class
            'class'
          else
            raise "Unexpected base type: #{inspect}"
          end
        end

        def has_self_type?
          self.is_a?(Types::Bases::Self)
        end

        def has_classish_type?
          self.is_a?(Bases::Instance) || self.is_a?(Bases::Class)
        end

        def with_nonreturn_void?
          self.is_a?(Bases::Void)
        end
      end

      class Bool < Base; end
      class Void < Base; end
      class Any < Base
        def initialize(location:, todo: false)
          super(location: location)
          if todo
            @string = "__todo__"
          end
        end

        def to_s(level=0)
          @string || "untyped"
        end
      end
      class Nil < Base; end
      class Top < Base; end
      class Bottom < Base; end
      class Self < Base; end
      class Instance < Base
        def sub(s)
          s.apply(self)
        end
      end
      class Class < Base; end
    end

    class Variable
      attr_reader :name
      attr_reader :location

      include NoTypeName

      def initialize(name:, location:)
        @name = name
        @location = location
      end

      def ==(other)
        other.is_a?(Variable) && other.name == name
      end

      alias eql? ==

      def hash
        self.class.hash ^ name.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          set << name
        end
      end

      def to_json(state = _ = nil)
        { class: :variable, name: name, location: location }.to_json(state)
      end

      def sub(s)
        s.apply(self)
      end

      def self.build(v)
        case v
        when Symbol
          new(name: v, location: nil)
        when Array
          v.map {|x| new(name: x, location: nil) }
        end
      end

      @@count = 0
      def self.fresh(v = :T)
        @@count = @@count + 1
        new(name: :"#{v}@#{@@count}", location: nil)
      end

      def to_s(level = 0)
        name.to_s
      end

      include EmptyEachType

      def has_self_type?
        false
      end

      def has_classish_type?
        false
      end

      def with_nonreturn_void?
        false
      end
    end

    class ClassSingleton
      attr_reader :name
      attr_reader :location

      def initialize(name:, location:)
        @name = name
        @location = location
      end

      def ==(other)
        other.is_a?(ClassSingleton) && other.name == name
      end

      alias eql? ==

      def hash
        self.class.hash ^ name.hash
      end

      include NoFreeVariables
      include NoSubst

      def to_json(state = _ = nil)
        { class: :class_singleton, name: name, location: location }.to_json(state)
      end

      def to_s(level = 0)
        "singleton(#{name})"
      end

      include EmptyEachType

      def map_type_name(&)
        ClassSingleton.new(
          name: yield(name, location, self),
          location: location
        )
      end

      def has_self_type?
        false
      end

      def has_classish_type?
        false
      end

      def with_nonreturn_void?
        false
      end
    end

    module Application
      attr_reader :name
      attr_reader :args

      def ==(other)
        other.is_a?(self.class) && other.name == name && other.args == args
      end

      alias eql? ==

      def hash
        name.hash ^ args.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          args.each do |arg|
            arg.free_variables(set)
          end
        end
      end

      def to_s(level = 0)
        if args.empty?
          name.to_s
        else
          "#{name}[#{args.join(", ")}]"
        end
      end

      def each_type(&block)
        if block
          args.each(&block)
        else
          enum_for :each_type
        end
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        each_type.any? do |type|
          if type.is_a?(Bases::Void)
            # `void` in immediate generics parameter is allowed
            false
          else
            type.with_nonreturn_void?
          end
        end
      end
    end

    class Interface
      attr_reader :location

      include Application

      def initialize(name:, args:, location:)
        @name = name
        @args = args
        @location = location
      end

      def to_json(state = _ = nil)
        { class: :interface, name: name, args: args, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(name: name,
                       args: args.map {|ty| ty.sub(s) },
                       location: location)
      end

      def map_type_name(&block)
        Interface.new(
          name: yield(name, location, self),
          args: args.map {|type| type.map_type_name(&block) },
          location: location
        )
      end

      def map_type(&block)
        if block
          Interface.new(
            name: name,
            args: args.map {|type| yield type },
            location: location
          )
        else
          enum_for(:map_type)
        end
      end
    end

    class ClassInstance
      attr_reader :location

      include Application

      def initialize(name:, args:, location:)
        @name = name
        @args = args
        @location = location
      end

      def to_json(state = _ = nil)
        { class: :class_instance, name: name, args: args, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(name: name,
                       args: args.map {|ty| ty.sub(s) },
                       location: location)
      end

      def map_type_name(&block)
        ClassInstance.new(
          name: yield(name, location, self),
          args: args.map {|type| type.map_type_name(&block) },
          location: location
        )
      end

      def map_type(&block)
        if block
          ClassInstance.new(
            name: name,
            args: args.map {|type| yield type },
            location: location
          )
        else
          enum_for :map_type
        end
      end
    end

    class Alias
      attr_reader :location

      include Application

      def initialize(name:, args:, location:)
        @name = name
        @args = args
        @location = location
      end

      def to_json(state = _ = nil)
        { class: :alias, name: name, args: args, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        Alias.new(name: name, args: args.map {|ty| ty.sub(s) }, location: location)
      end

      def map_type_name(&block)
        Alias.new(
          name: yield(name, location, self),
          args: args.map {|arg| arg.map_type_name(&block) },
          location: location
        )
      end

      def map_type(&block)
        if block
          Alias.new(
            name: name,
            args: args.map {|type| yield type },
            location: location
          )
        else
          enum_for :map_type
        end
      end
    end

    class Tuple
      attr_reader :types
      attr_reader :location

      def initialize(types:, location:)
        @types = types
        @location = location
      end

      def ==(other)
        other.is_a?(Tuple) && other.types == types
      end

      alias eql? ==

      def hash
        self.class.hash ^ types.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          types.each do |type|
            type.free_variables set
          end
        end
      end

      def to_json(state = _ = nil)
        { class: :tuple, types: types, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(types: types.map {|ty| ty.sub(s) },
                       location: location)
      end

      def to_s(level = 0)
        if types.empty?
          "[ ]"
        else
          "[ #{types.join(", ")} ]"
        end
      end

      def each_type(&block)
        if block
          types.each(&block)
        else
          enum_for :each_type
        end
      end

      def map_type_name(&block)
        Tuple.new(
          types: types.map {|type| type.map_type_name(&block) },
          location: location
        )
      end

      def map_type(&block)
        if block
          Tuple.new(
            types: types.map {|type| yield type },
            location: location
          )
        else
          enum_for :map_type
        end
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        each_type.any? {|type| type.with_nonreturn_void? }
      end
    end

    class Record
      attr_reader :all_fields, :fields, :optional_fields
      attr_reader :location

      def initialize(all_fields: nil, fields: nil, location:)
        case
        when fields && all_fields.nil?
          @all_fields = fields.transform_values { |v| [v, true] }
          @fields = fields
          @optional_fields = {}
        when all_fields && fields.nil?
          @all_fields = all_fields
          @fields = {}
          @optional_fields = {}
          all_fields.each do |(k, (v, required))|
            if required
              @fields[k] = v
            else
              @optional_fields[k] = v
            end
          end
        else
          raise ArgumentError, "only one of `:fields` or `:all_fields` is required"
        end

        @location = location
      end

      def ==(other)
        other.is_a?(Record) && other.fields == fields && other.optional_fields == optional_fields
      end

      alias eql? ==

      def hash
        self.class.hash ^ all_fields.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          fields.each_value do |type|
            type.free_variables set
          end
          optional_fields.each_value do |type|
            type.free_variables set
          end
        end
      end

      def to_json(state = _ = nil)
        { class: :record, fields: fields, optional_fields: optional_fields, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(
          all_fields: all_fields.transform_values {|ty, required| [ty.sub(s), required] },
          location: location
        )
      end

      def to_s(level = 0)
        return "{ }" if all_fields.empty?

        fields = all_fields.map do |key, (type, required)|
          field = if key.is_a?(Symbol) && key.match?(/\A[A-Za-z_][A-Za-z_0-9]*\z/)
            "#{key}: #{type}"
          else
            "#{key.inspect} => #{type}"
          end

          field = "?#{field}" unless required
          field
        end
        "{ #{fields.join(", ")} }"
      end

      def each_type(&block)
        if block
          fields.each_value(&block)
          optional_fields.each_value(&block)
        else
          enum_for :each_type
        end
      end

      def map_type_name(&block)
        Record.new(
          all_fields: all_fields.transform_values {|ty, required| [ty.map_type_name(&block), required] },
          location: location
        )
      end

      def map_type(&block)
        if block
          Record.new(
            all_fields: all_fields.transform_values {|type, required| [yield(type), required] },
            location: location
          )
        else
          enum_for :map_type
        end
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        each_type.any? {|type| type.with_nonreturn_void? }
      end
    end

    class Optional
      attr_reader :type
      attr_reader :location

      def initialize(type:, location:)
        @type = type
        @location = location
      end

      def ==(other)
        other.is_a?(Optional) && other.type == type
      end

      alias eql? ==

      def hash
        self.class.hash ^ type.hash
      end

      def free_variables(set = Set.new)
        type.free_variables(set)
      end

      def to_json(state = _ = nil)
        { class: :optional, type: type, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(type: type.sub(s), location: location)
      end

      def to_s(level = 0)
        case t = type
        when RBS::Types::Literal
          case t.literal
          when Symbol
            return "#{type.to_s(1)} ?"
          end
        when RBS::Types::Proc
          return "(#{type.to_s(1)})?"
        end

        "#{type.to_s(1)}?"
      end

      def each_type
        if block_given?
          yield type
        else
          enum_for :each_type
        end
      end

      def map_type_name(&block)
        Optional.new(
          type: type.map_type_name(&block),
          location: location
        )
      end

      def map_type(&block)
        if block
          Optional.new(
            type: yield(type),
            location: location
          )
        else
          enum_for :map_type
        end
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        each_type.any? {|type| type.with_nonreturn_void? }
      end
    end

    class Union
      attr_reader :types
      attr_reader :location

      def initialize(types:, location:)
        @types = types
        @location = location
      end

      def ==(other)
        other.is_a?(Union) && other.types == types
      end

      alias eql? ==

      def hash
        self.class.hash ^ types.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          types.each do |type|
            type.free_variables set
          end
        end
      end

      def to_json(state = _ = nil)
        { class: :union, types: types, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(types: types.map {|ty| ty.sub(s) },
                       location: location)
      end

      def to_s(level = 0)
        strs = types.map do |ty|
          case ty
          when Intersection
            ty.to_s([1, level].max)
          else
            ty.to_s
          end
        end

        if level > 0
          "(#{strs.join(" | ")})"
        else
          strs.join(" | ")
        end
      end

      def each_type(&block)
        if block
          types.each(&block)
        else
          enum_for :each_type
        end
      end

      def map_type(&block)
        if block
          Union.new(types: types.map(&block), location: location)
        else
          enum_for :map_type
        end
      end

      def map_type_name(&block)
        Union.new(
          types: types.map {|type| type.map_type_name(&block) },
          location: location
        )
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        each_type.any? {|type| type.with_nonreturn_void? }
      end
    end

    class Intersection
      attr_reader :types
      attr_reader :location

      def initialize(types:, location:)
        @types = types
        @location = location
      end

      def ==(other)
        other.is_a?(Intersection) && other.types == types
      end

      alias eql? ==

      def hash
        self.class.hash ^ types.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          types.each do |type|
            type.free_variables set
          end
        end
      end

      def to_json(state = _ = nil)
        { class: :intersection, types: types, location: location }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(types: types.map {|ty| ty.sub(s) },
                       location: location)
      end

      def to_s(level = 0)
        strs = types.map {|ty| ty.to_s(2) }
        if level > 0
          "(#{strs.join(" & ")})"
        else
          strs.join(" & ")
        end
      end

      def each_type(&block)
        if block
          types.each(&block)
        else
          enum_for :each_type
        end
      end

      def map_type(&block)
        if block
          Intersection.new(types: types.map(&block), location: location)
        else
          enum_for :map_type
        end
      end

      def map_type_name(&block)
        Intersection.new(
          types: types.map {|type| type.map_type_name(&block) },
          location: location
        )
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        each_type.any? {|type| type.with_nonreturn_void? }
      end
    end

    class Function
      class Param
        attr_reader :type
        attr_reader :name
        attr_reader :location

        def initialize(type:, name:, location: nil)
          @type = type
          @name = name
          @location = location
        end

        def ==(other)
          other.is_a?(Param) && other.type == type && other.name == name
        end

        alias eql? ==

        def hash
          self.class.hash ^ type.hash ^ name.hash
        end

        def map_type(&block)
          if block
            Param.new(name: name, type: yield(type), location: location)
          else
            enum_for :map_type
          end
        end

        def to_json(state = _ = nil)
          { type: type, name: name }.to_json(state)
        end

        def to_s
          if name
            if name.match?(/\A[a-zA-Z0-9_]+\z/)
              "#{type} #{name}"
            else
              "#{type} `#{name}`"
            end
          else
            "#{type}"
          end
        end
      end

      attr_reader :required_positionals
      attr_reader :optional_positionals
      attr_reader :rest_positionals
      attr_reader :trailing_positionals
      attr_reader :required_keywords
      attr_reader :optional_keywords
      attr_reader :rest_keywords
      attr_reader :return_type

      def initialize(required_positionals:, optional_positionals:, rest_positionals:, trailing_positionals:, required_keywords:, optional_keywords:, rest_keywords:, return_type:)
        @return_type = return_type
        @required_positionals = required_positionals
        @optional_positionals = optional_positionals
        @rest_positionals = rest_positionals
        @trailing_positionals = trailing_positionals
        @required_keywords = required_keywords
        @optional_keywords = optional_keywords
        @rest_keywords = rest_keywords
      end

      def ==(other)
        other.is_a?(Function) &&
          other.required_positionals == required_positionals &&
          other.optional_positionals == optional_positionals &&
          other.rest_positionals == rest_positionals &&
          other.trailing_positionals == trailing_positionals &&
          other.required_keywords == required_keywords &&
          other.optional_keywords == optional_keywords &&
          other.rest_keywords == rest_keywords &&
          other.return_type == return_type
      end

      alias eql? ==

      def hash
        self.class.hash ^
          required_positionals.hash ^
          optional_positionals.hash ^
          rest_positionals.hash ^
          trailing_positionals.hash ^
          required_keywords.hash ^
          optional_keywords.hash ^
          rest_keywords.hash ^
          return_type.hash
      end

      def free_variables(set = Set.new)
        set.tap do
          required_positionals.each do |param|
            param.type.free_variables(set)
          end
          optional_positionals.each do |param|
            param.type.free_variables(set)
          end
          rest_positionals&.yield_self do |param|
            param.type.free_variables(set)
          end
          trailing_positionals.each do |param|
            param.type.free_variables(set)
          end
          required_keywords.each_value do |param|
            param.type.free_variables(set)
          end
          optional_keywords.each_value do |param|
            param.type.free_variables(set)
          end
          rest_keywords&.yield_self do |param|
            param.type.free_variables(set)
          end

          return_type.free_variables(set)
        end
      end

      def map_type(&block)
        if block
          Function.new(
            required_positionals: amap(required_positionals) {|param| param.map_type(&block) },
            optional_positionals: amap(optional_positionals) {|param| param.map_type(&block) },
            rest_positionals: rest_positionals&.yield_self {|param| param.map_type(&block) },
            trailing_positionals: amap(trailing_positionals) {|param| param.map_type(&block) },
            required_keywords: hmapv(required_keywords) {|param| param.map_type(&block) },
            optional_keywords: hmapv(optional_keywords) {|param| param.map_type(&block) },
            rest_keywords: rest_keywords&.yield_self {|param| param.map_type(&block) },
            return_type: yield(return_type)
          )
        else
          enum_for :map_type
        end
      end

      def amap(array, &block)
        if array.empty?
          _ = array
        else
          array.map(&block)
        end
      end

      def hmapv(hash, &block)
        if hash.empty?
          _ = hash
        else
          hash.transform_values(&block)
        end
      end

      def map_type_name(&block)
        map_type do |type|
          type.map_type_name(&block)
        end
      end

      def each_type
        if block_given?
          required_positionals.each {|param| yield param.type }
          optional_positionals.each {|param| yield param.type }
          rest_positionals&.yield_self {|param| yield param.type }
          trailing_positionals.each {|param| yield param.type }
          required_keywords.each_value {|param| yield param.type }
          optional_keywords.each_value {|param| yield param.type }
          rest_keywords&.yield_self {|param| yield param.type }
          yield(return_type)
        else
          enum_for :each_type
        end
      end

      def each_param(&block)
        if block
          required_positionals.each(&block)
          optional_positionals.each(&block)
          rest_positionals&.yield_self(&block)
          trailing_positionals.each(&block)
          required_keywords.each_value(&block)
          optional_keywords.each_value(&block)
          rest_keywords&.yield_self(&block)
        else
          enum_for :each_param
        end
      end

      def to_json(state = _ = nil)
        {
          required_positionals: required_positionals,
          optional_positionals: optional_positionals,
          rest_positionals: rest_positionals,
          trailing_positionals: trailing_positionals,
          required_keywords: required_keywords,
          optional_keywords: optional_keywords,
          rest_keywords: rest_keywords,
          return_type: return_type
        }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        map_type {|ty| ty.sub(s) }
      end

      def self.empty(return_type)
        Function.new(
          required_positionals: [],
          optional_positionals: [],
          rest_positionals: nil,
          trailing_positionals: [],
          required_keywords: {},
          optional_keywords: {},
          rest_keywords: nil,
          return_type: return_type
        )
      end

      def with_return_type(type)
        Function.new(
          required_positionals: required_positionals,
          optional_positionals: optional_positionals,
          rest_positionals: rest_positionals,
          trailing_positionals: trailing_positionals,
          required_keywords: required_keywords,
          optional_keywords: optional_keywords,
          rest_keywords: rest_keywords,
          return_type: type
        )
      end

      def update(required_positionals: self.required_positionals, optional_positionals: self.optional_positionals, rest_positionals: self.rest_positionals, trailing_positionals: self.trailing_positionals,
                 required_keywords: self.required_keywords, optional_keywords: self.optional_keywords, rest_keywords: self.rest_keywords, return_type: self.return_type)
        Function.new(
          required_positionals: required_positionals,
          optional_positionals: optional_positionals,
          rest_positionals: rest_positionals,
          trailing_positionals: trailing_positionals,
          required_keywords: required_keywords,
          optional_keywords: optional_keywords,
          rest_keywords: rest_keywords,
          return_type: return_type
        )
      end

      def empty?
        required_positionals.empty? &&
          optional_positionals.empty? &&
          !rest_positionals &&
          trailing_positionals.empty? &&
          required_keywords.empty? &&
          optional_keywords.empty? &&
          !rest_keywords
      end

      def param_to_s
        # @type var params: Array[String]
        params = []

        params.push(*required_positionals.map(&:to_s))
        params.push(*optional_positionals.map {|p| "?#{p}"})
        params.push("*#{rest_positionals}") if rest_positionals
        params.push(*trailing_positionals.map(&:to_s))
        params.push(*required_keywords.map {|name, param| "#{name}: #{param}" })
        params.push(*optional_keywords.map {|name, param| "?#{name}: #{param}" })
        params.push("**#{rest_keywords}") if rest_keywords

        params.join(", ")
      end

      def return_to_s
        return_type.to_s(1)
      end

      def drop_head
        case
        when !required_positionals.empty?
          [
            required_positionals[0],
            update(required_positionals: required_positionals.drop(1))
          ]
        when !optional_positionals.empty?
          [
            optional_positionals[0],
            update(optional_positionals: optional_positionals.drop(1))
          ]
        else
          raise "Cannot #drop_head"
        end
      end

      def drop_tail
        case
        when !trailing_positionals.empty?
          last = trailing_positionals.last or raise
          [
            last,
            update(trailing_positionals: trailing_positionals.take(trailing_positionals.size - 1))
          ]
        else
          raise "Cannot #drop_tail"
        end
      end

      def has_keyword?
        if !required_keywords.empty? || !optional_keywords.empty? || rest_keywords
          true
        else
          false
        end
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        if each_param.any? {|param| param.type.with_nonreturn_void? }
          true
        else
          if return_type.is_a?(Bases::Void)
            false
          else
            return_type.with_nonreturn_void?
          end
        end
      end
    end

    class UntypedFunction
      attr_reader :return_type

      def initialize(return_type:)
        @return_type = return_type
      end

      def free_variables(acc = Set.new)
        return_type.free_variables(acc)
      end

      def map_type(&block)
        if block
          update(return_type: yield(return_type))
        else
          enum_for :map_type
        end
      end

      def map_type_name(&block)
        UntypedFunction.new(
          return_type: return_type.map_type_name(&block)
        )
      end

      def each_type(&block)
        if block
          yield return_type
        else
          enum_for :each_type
        end
      end

      def each_param(&block)
        if block
          # noop
        else
          enum_for :each_param
        end
      end

      def to_json(state = _ = nil)
        {
          return_type: return_type
        }.to_json(state)
      end

      def sub(subst)
        return self if subst.empty?

        map_type { _1.sub(subst) }
      end

      def with_return_type(ty)
        update(return_type: ty)
      end

      def update(return_type: self.return_type)
        UntypedFunction.new(return_type: return_type)
      end

      def empty?
        true
      end

      def has_self_type?
        return_type.has_self_type?
      end

      def has_classish_type?
        return_type.has_classish_type?
      end

      def with_nonreturn_void?
        false
      end

      def param_to_s
        "?"
      end

      def return_to_s
        return_type.to_s(1)
      end

      def ==(other)
        other.is_a?(UntypedFunction) && other.return_type == return_type
      end

      alias eql? ==

      def hash
        self.class.hash ^ return_type.hash
      end

    end

    class Block
      attr_reader :type
      attr_reader :required
      attr_reader :self_type
      attr_reader :location

      def initialize(location: nil, type:, required:, self_type: nil)
        @location = location
        @type = type
        @required = required ? true : false
        @self_type = self_type
      end

      def ==(other)
        other.is_a?(Block) &&
          other.type == type &&
          other.required == required &&
          other.self_type == self_type
      end

      def to_json(state = _ = nil)
        {
          type: type,
          required: required,
          self_type: self_type
        }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(
          type: type.sub(s),
          required: required,
          self_type: self_type&.sub(s)
        )
      end

      def map_type(&block)
        Block.new(
          required: required,
          type: type.map_type(&block),
          self_type: self_type ? yield(self_type) : nil
        )
      end
    end

    module SelfTypeBindingHelper
      module_function

      def self_type_binding_to_s(t)
        if t
          "[self: #{t}] "
        else
          ""
        end
      end
    end

    class Proc
      attr_reader :type
      attr_reader :block
      attr_reader :self_type
      attr_reader :location

      def initialize(location:, type:, block:, self_type: nil)
        @type = type
        @block = block
        @location = location
        @self_type = self_type
      end

      def ==(other)
        other.is_a?(Proc) && other.type == type && other.block == block && other.self_type == self_type
      end

      alias eql? ==

      def hash
        self.class.hash ^ type.hash ^ block.hash ^ self_type.hash
      end

      def free_variables(set = Set[])
        type.free_variables(set)
        block&.type&.free_variables(set)
        self_type&.free_variables(set)
        set
      end

      def to_json(state = _ = nil)
        {
          class: :proc,
          type: type,
          block: block,
          location: location,
          self_type: self_type
        }.to_json(state)
      end

      def sub(s)
        return self if s.empty?

        self.class.new(
          type: type.sub(s),
          block: block&.sub(s),
          self_type: self_type&.sub(s),
          location: location
        )
      end

      def to_s(level = 0)
        self_binding = SelfTypeBindingHelper.self_type_binding_to_s(self_type)
        block_self_binding = SelfTypeBindingHelper.self_type_binding_to_s(block&.self_type)

        case
        when b = block
          if b.required
            "^(#{type.param_to_s}) #{self_binding}{ (#{b.type.param_to_s}) #{block_self_binding}-> #{b.type.return_to_s} } -> #{type.return_to_s}"
          else
            "^(#{type.param_to_s}) #{self_binding}?{ (#{b.type.param_to_s}) #{block_self_binding}-> #{b.type.return_to_s} } -> #{type.return_to_s}"
          end
        else
          "^(#{type.param_to_s}) #{self_binding}-> #{type.return_to_s}"
        end
      end

      def each_type(&block)
        if block
          type.each_type(&block)
          yield self_type if self_type
          self.block&.type&.each_type(&block)
          if self_type = self.block&.self_type
            yield self_type
          end
        else
          enum_for :each_type
        end
      end

      def map_type_name(&block)
        Proc.new(
          type: type.map_type_name(&block),
          block: self.block&.map_type {|type| type.map_type_name(&block) },
          self_type: self_type&.map_type_name(&block),
          location: location
        )
      end

      def map_type(&block)
        if block
          Proc.new(
            type: type.map_type(&block),
            block: self.block&.map_type(&block),
            self_type: self_type ? yield(self_type) : nil,
            location: location
          )
        else
          enum_for :map_type
        end
      end

      def has_self_type?
        each_type.any? {|type| type.has_self_type? }
      end

      def has_classish_type?
        each_type.any? {|type| type.has_classish_type? }
      end

      def with_nonreturn_void?
        if type.with_nonreturn_void? || self_type&.with_nonreturn_void?
          true
        else
          if block = block()
            block.type.with_nonreturn_void? || block.self_type&.with_nonreturn_void? || false
          else
            false
          end
        end
      end
    end

    class Literal
      attr_reader :literal
      attr_reader :location

      def initialize(literal:, location:)
        @literal = literal
        @location = location
      end

      def ==(other)
        other.is_a?(Literal) && other.literal == literal
      end

      alias eql? ==

      def hash
        self.class.hash ^ literal.hash
      end

      include NoFreeVariables
      include NoSubst
      include EmptyEachType
      include NoTypeName

      def to_json(state = _ = nil)
        { class: :literal, literal: literal.inspect, location: location }.to_json(state)
      end

      def to_s(level = 0)
        literal.inspect
      end

      def has_self_type?
        false
      end

      def has_classish_type?
        false
      end

      def with_nonreturn_void?
        false
      end

      TABLE = {
        "\\a" => "\a",
        "\\b" => "\b",
        "\\e" => "\033",
        "\\f" => "\f",
        "\\n" => "\n",
        "\\r" => "\r",
        "\\s" => " ",
        "\\t" => "\t",
        "\\v" => "\v",
        "\\\"" => "\"",
        "\\\'" => "'",
        "\\\\" => "\\",
        "\\" => ""
      }

      def self.unescape_string(string, is_double_quote)
        if is_double_quote
          string.gsub!(/\\([0-9]{1,3})/) { ($1 || "").to_i(8).chr }
          string.gsub!(/\\x([0-9a-f]{1,2})/) { ($1 || "").to_i(16).chr }
          string.gsub!(/\\u([0-9a-fA-F]{4})/) { ($1 || "").to_i(16).chr(Encoding::UTF_8) }
          string.gsub!(/\\[abefnrstv"'\\]?/, TABLE)
          string
        else
          string.gsub!(/\\['\\]/, TABLE)
          string
        end
      end
    end
  end
end
