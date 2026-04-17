# frozen_string_literal: true

module RBS
  class Definition
    class Variable
      attr_reader :parent_variable
      attr_reader :type
      attr_reader :declared_in
      attr_reader :source

      def initialize(parent_variable:, type:, declared_in:, source:)
        @parent_variable = parent_variable
        @type = type
        @declared_in = declared_in
        @source = source
      end

      def sub(s)
        return self if s.empty?

        self.class.new(
          parent_variable: parent_variable,
          type: type.sub(s),
          declared_in: declared_in,
          source: source
        )
      end
    end

    class Method
      class TypeDef
        attr_reader :type
        attr_reader :member
        attr_reader :defined_in
        attr_reader :implemented_in
        attr_reader :member_annotations
        attr_reader :overload_annotations
        attr_reader :annotations

        def initialize(type:, member:, defined_in:, implemented_in:, overload_annotations: [])
          @type = type
          @member = member
          @defined_in = defined_in
          @implemented_in = implemented_in
          @member_annotations = []
          @overload_annotations = []
          @annotations = []
        end

        def ==(other)
          other.is_a?(TypeDef) &&
            other.type == type &&
            other.member == member &&
            other.defined_in == defined_in &&
            other.implemented_in == implemented_in
        end

        alias eql? ==

        def hash
          self.class.hash ^ type.hash ^ member.hash ^ defined_in.hash ^ implemented_in.hash
        end

        def comment
          member.comment
        end

        def update(type: self.type, member: self.member, defined_in: self.defined_in, implemented_in: self.implemented_in)
          TypeDef.new(type: type, member: member, defined_in: defined_in, implemented_in: implemented_in).tap do |type_def|
            type_def.overload_annotations.replace(self.overload_annotations)
            type_def.member_annotations.replace(self.member_annotations)
          end
        end

        def overload?
          case mem = member
          when AST::Members::MethodDefinition
            mem.overloading?
          else
            false
          end
        end

        def each_annotation(&block)
          if block
            member_annotations.each(&block)
            overload_annotations.each(&block)
          else
            enum_for :each_annotation
          end
        end
      end

      attr_reader :super_method
      attr_reader :defs
      attr_reader :accessibility
      attr_reader :extra_annotations
      attr_reader :annotations
      attr_reader :alias_of
      attr_reader :alias_member

      def initialize(super_method:, defs:, accessibility:, annotations: [], alias_of:, alias_member: nil)
        @super_method = super_method
        @defs = defs
        @accessibility = accessibility
        @extra_annotations = []
        @annotations = []
        @alias_of = alias_of
        @alias_member = alias_member
      end

      def ==(other)
        other.is_a?(Method) &&
          other.super_method == super_method &&
          other.defs == defs &&
          other.accessibility == accessibility &&
          other.annotations == annotations &&
          other.alias_of == alias_of &&
          other.alias_member == alias_member
      end

      alias eql? ==

      def hash
        self.class.hash ^ super_method.hash ^ defs.hash ^ accessibility.hash ^ annotations.hash ^ alias_of.hash
      end

      def defined_in
        @defined_in ||= begin
          last_def = defs.last or raise
          last_def.defined_in
        end
      end

      def implemented_in
        @implemented_in ||= begin
          last_def = defs.last or raise
          last_def.implemented_in
        end
      end

      def method_types
        @method_types ||= defs.map(&:type)
      end

      def comments
        @comments ||= defs.map(&:comment).compact.uniq
      end

      def members
        @members ||= defs.map(&:member).uniq
      end

      def public?
        @accessibility == :public
      end

      def private?
        @accessibility == :private
      end

      def sub(s)
        return self if s.empty?

        update(
          super_method: super_method&.sub(s),
          defs: defs.map {|defn| defn.update(type: defn.type.sub(s)) }
        )
      end

      def map_type(&block)
        update(
          super_method: super_method&.map_type(&block),
          defs: defs.map {|defn| defn.update(type: defn.type.map_type(&block)) }
        )
      end

      def map_type_bound(&block)
        update(
          super_method: super_method&.map_type_bound(&block),
          defs: defs.map {|defn| defn.update(type: defn.type.map_type_bound(&block)) }
        )
      end

      def map_method_type(&block)
        update(
          defs: defs.map {|defn| defn.update(type: yield(defn.type)) },
        )
      end

      def update(super_method: self.super_method, defs: self.defs, accessibility: self.accessibility, alias_of: self.alias_of, annotations: self.annotations, alias_member: self.alias_member)
        self.class.new(
          super_method: super_method,
          defs: defs,
          accessibility: accessibility,
          alias_of: alias_of,
          alias_member: alias_member
        ).tap do |method|
          method.annotations.replace(annotations)
        end
      end
    end

    module Ancestor
      class Instance
        attr_reader :name, :args, :source

        def initialize(name:, args:, source:)
          @name = name
          @args = args
          @source = source
        end

        def ==(other)
          other.is_a?(Instance) && other.name == name && other.args == args
        end

        alias eql? ==

        def hash
          self.class.hash ^ name.hash ^ args.hash
        end
      end

      class Singleton
        attr_reader :name

        def initialize(name:)
          @name = name
        end

        def ==(other)
          other.is_a?(Singleton) && other.name == name
        end

        alias eql? ==

        def hash
          self.class.hash ^ name.hash
        end
      end
    end

    class InstanceAncestors
      attr_reader :type_name
      attr_reader :params
      attr_reader :ancestors

      def initialize(type_name:, params:, ancestors:)
        @type_name = type_name
        @params = params
        @ancestors = ancestors
      end

      def apply(args, env:, location:)
        InvalidTypeApplicationError.check2!(env: env, type_name: type_name, args: args, location: location)

        subst = Substitution.build(params, args)

        ancestors.map do |ancestor|
          case ancestor
          when Ancestor::Instance
            if ancestor.args.empty?
              ancestor
            else
              Ancestor::Instance.new(
                name: ancestor.name,
                args: ancestor.args.map {|type| type.sub(subst) },
                source: ancestor.source
              )
            end
          when Ancestor::Singleton
            ancestor
          end
        end
      end
    end

    class SingletonAncestors
      attr_reader :type_name
      attr_reader :ancestors

      def initialize(type_name:, ancestors:)
        @type_name = type_name
        @ancestors = ancestors
      end
    end

    attr_reader :type_name
    attr_reader :entry
    attr_reader :ancestors
    attr_reader :self_type
    attr_reader :methods
    attr_reader :instance_variables
    attr_reader :class_variables

    def initialize(type_name:, entry:, self_type:, ancestors:)
      case entry
      when Environment::ClassEntry, Environment::ModuleEntry
        # ok
      else
        unless entry.decl.is_a?(AST::Declarations::Interface)
          raise "Declaration should be a class, module, or interface: #{type_name}"
        end
      end

      unless self_type.is_a?(Types::ClassSingleton) || self_type.is_a?(Types::Interface) || self_type.is_a?(Types::ClassInstance)
        raise "self_type should be the type of declaration: #{self_type}"
      end

      @type_name = type_name
      @self_type = self_type
      @entry = entry
      @methods = {}
      @instance_variables = {}
      @class_variables = {}
      @ancestors = ancestors
    end

    def class?
      entry.is_a?(Environment::ClassEntry)
    end

    def module?
      entry.is_a?(Environment::ModuleEntry)
    end

    def interface?
      case en = entry
      when Environment::SingleEntry
        en.decl.is_a?(AST::Declarations::Interface)
      else
        false
      end
    end

    def class_type?
      self_type.is_a?(Types::ClassSingleton)
    end

    def instance_type?
      self_type.is_a?(Types::ClassInstance)
    end

    def interface_type?
      self_type.is_a?(Types::Interface)
    end

    def type_params
      type_params_decl.each.map(&:name)
    end

    def type_params_decl
      case en = entry
      when Environment::ClassEntry, Environment::ModuleEntry
        en.type_params
      when Environment::SingleEntry
        en.decl.type_params
      end
    end

    def sub(s)
      return self if s.empty?

      definition = self.class.new(type_name: type_name, self_type: _ = self_type.sub(s), ancestors: ancestors, entry: entry)

      definition.methods.merge!(methods.transform_values {|method| method.sub(s) })
      definition.instance_variables.merge!(instance_variables.transform_values {|v| v.sub(s) })
      definition.class_variables.merge!(class_variables.transform_values {|v| v.sub(s) })

      definition
    end

    def map_method_type(&block)
      definition = self.class.new(type_name: type_name, self_type: self_type, ancestors: ancestors, entry: entry)

      definition.methods.merge!(methods.transform_values {|method| method.map_method_type(&block) })
      definition.instance_variables.merge!(instance_variables)
      definition.class_variables.merge!(class_variables)

      definition
    end

    def each_type(&block)
      if block
        methods.each_value do |method|
          if method.defined_in == type_name
            method.method_types.each do |method_type|
              method_type.each_type(&block)
            end
          end
        end

        instance_variables.each_value do |var|
          if var.declared_in == type_name
            yield var.type
          end
        end

        class_variables.each_value do |var|
          if var.declared_in == type_name
            yield var.type
          end
        end
      else
        enum_for :each_type
      end
    end
  end
end
