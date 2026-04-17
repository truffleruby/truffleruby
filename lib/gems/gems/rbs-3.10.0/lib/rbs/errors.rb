# frozen_string_literal: true

module RBS
  module MethodNameHelper
    def method_name_string()
      separator = case kind
                  when :instance
                    "#"
                  when :singleton
                    "."
                  else
                    raise
                  end

      "#{type_name}#{separator}#{method_name}"
    end
  end

  class BaseError < StandardError; end
  class LoadingError < BaseError; end
  class DefinitionError < BaseError; end

  module DetailedMessageable
    def detailed_message(highlight: false, **)
      msg = if Exception.method_defined?(:detailed_message)
        super
      else
        # Failback to `#message` in Ruby 3.1 or earlier
        "#{message} (#{self.class.name})"
      end

      return msg unless location

      # Support only one line
      return msg unless location.start_line == location.end_line

      indent = " " * location.start_column
      marker = "^" * ([location.end_column - location.start_column, 1].max or raise)

      io = StringIO.new
      io.puts msg
      io.puts
      io.print "\e[1m" if highlight
      io.puts "  #{location.buffer.lines[location.end_line - 1]}"
      io.puts "  #{indent}#{marker}"
      io.print "\e[m" if highlight
      io.string
    end
  end

  class ParsingError < BaseError
    include DetailedMessageable

    attr_reader :location
    attr_reader :error_message
    attr_reader :token_type

    def initialize(location, error_message, token_type)
      @location = location
      @error_message = error_message
      @token_type = token_type

      super "#{Location.to_string location}: Syntax error: #{error_message}, token=`#{location.source}` (#{token_type})"
    end
  end

  class InvalidTypeApplicationError < DefinitionError
    attr_reader :type_name
    attr_reader :args
    attr_reader :params
    attr_reader :type_params
    attr_reader :location

    def initialize(type_name:, args:, params:, location:)
      @type_name = type_name
      @args = args
      @type_params = params
      @params = params.map(&:name)
      @location = location
      super "#{Location.to_string location}: #{type_name} expects parameters [#{params.join(", ")}], but given args [#{args.join(", ")}]"
    end

    def self.check!(type_name:, args:, params:, location:)
      min_arity = params.count { _1.default_type.nil? }
      max_arity = params.size

      unless min_arity <= args.size && args.size <= max_arity
        raise new(type_name: type_name, args: args, params: params, location: location)
      end
    end

    def self.check2!(env:, type_name:, args:, location:)
      params =
        case
        when type_name.class?
          decl = env.module_class_entry(type_name, normalized: true) or raise
          decl.type_params
        when type_name.interface?
          env.interface_decls.fetch(type_name).decl.type_params
        when type_name.alias?
          env.type_alias_decls.fetch(type_name).decl.type_params
        else
          raise
        end

      check!(type_name: type_name, args: args, params: params, location: location)
    end
  end

  class RecursiveAncestorError < DefinitionError
    attr_reader :ancestors
    attr_reader :location

    def initialize(ancestors:, location:)
      @ancestors = ancestors
      @location = location

      names = ancestors.map do |ancestor|
        case ancestor
        when Definition::Ancestor::Singleton
          "singleton(#{ancestor.name})"
        when Definition::Ancestor::Instance
          if ancestor.args.empty?
            ancestor.name.to_s
          else
            "#{ancestor.name}[#{ancestor.args.join(", ")}]"
          end
        end
      end

      super "#{Location.to_string location}: Detected recursive ancestors: #{names.join(" < ")}"
    end

    def self.check!(self_ancestor, ancestors:, location:)
      case self_ancestor
      when Definition::Ancestor::Instance
        if ancestors.any? {|a| a.is_a?(Definition::Ancestor::Instance) && a.name == self_ancestor.name }
          raise new(ancestors: ancestors + [self_ancestor], location: location)
        end
      when Definition::Ancestor::Singleton
        if ancestors.include?(self_ancestor)
          raise new(ancestors: ancestors + [self_ancestor], location: location)
        end
      end
    end
  end

  class NoTypeFoundError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :location

    def initialize(type_name:, location:)
      @type_name = type_name
      @location = location

      super "#{Location.to_string location}: Could not find #{type_name}"
    end

    def self.check!(type_name, env:, location:)
      env.type_name?(type_name) or raise new(type_name: type_name, location: location)
      type_name
    end
  end

  class NoSuperclassFoundError < DefinitionError
    attr_reader :type_name
    attr_reader :location

    def initialize(type_name:, location:)
      @type_name = type_name
      @location = location

      super "#{Location.to_string location}: Could not find super class: #{type_name}"
    end

    def self.check!(type_name, env:, location:)
      if env.module_name?(type_name)
        return
      end

      raise new(type_name: type_name, location: location)
    end
  end

  class InheritModuleError < DefinitionError
    include DetailedMessageable

    attr_reader :super_decl

    def initialize(super_decl)
      @super_decl = super_decl

      super "#{Location.to_string(super_decl.location)}: Cannot inherit a module: #{super_decl.name}"
    end

    def location
      @super_decl.location
    end

    def self.check!(super_decl, env:)
      super_name = env.normalize_type_name(super_decl.name)
      return if env.class_decl?(super_name) || env.class_alias?(super_name)

      raise new(super_decl)
    end
  end

  class NoSelfTypeFoundError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :location

    def initialize(type_name:, location:)
      @type_name = type_name
      @location = location

      super "#{Location.to_string location}: Could not find self type: #{type_name}"
    end

    def self.check!(self_type, env:)
      self_name = env.normalize_type_name(self_type.name)
      (env.module_name?(self_name) || env.interface_name?(self_name)) or raise new(type_name: self_type.name, location: self_type.location)
    end
  end

  class NoMixinFoundError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :member

    def initialize(type_name:, member:)
      @type_name = type_name
      @member = member

      super "#{Location.to_string location}: Could not find mixin: #{type_name}"
    end

    def location
      member.location
    end

    def self.check!(type_name, env:, member:)
      (env.module_name?(type_name) || env.interface_name?(type_name)) or raise new(type_name: type_name, member: member)
    end
  end

  class DuplicatedMethodDefinitionError < DefinitionError
    include DetailedMessageable

    attr_reader :type
    attr_reader :method_name
    attr_reader :members

    def initialize(type:, method_name:, members:)
      @type = type
      @method_name = method_name
      @members = members

      message = +"#{Location.to_string location}: #{qualified_method_name} has duplicated definitions"
      if members.size > 1
        message << " in #{other_locations.map { |loc| Location.to_string loc }.join(', ')}"
      end
      super message
    end

    def qualified_method_name
      case type
      when Types::ClassSingleton
        "#{type.name}.#{method_name}"
      else
        "#{type.name}##{method_name}"
      end
    end

    def type_name
      type.name
    end

    def location
      members[0].location
    end

    def other_locations
      members.drop(1).map(&:location)
    end
  end

  class DuplicatedInterfaceMethodDefinitionError < DefinitionError
    include DetailedMessageable

    attr_reader :type
    attr_reader :method_name
    attr_reader :member

    def initialize(type:, method_name:, member:)
      @type = type
      @method_name = method_name
      @member = member

      super "#{member.location}: Duplicated method definition: #{qualified_method_name}"
    end

    def location
      member.location
    end

    def qualified_method_name
      case type
      when Types::ClassSingleton
        "#{type.name}.#{method_name}"
      else
        "#{type.name}##{method_name}"
      end
    end

    def type_name
      type.name
    end
  end

  class VariableDuplicationError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :variable_name
    attr_reader :location

    def initialize(type_name:, variable_name:, location:)
      @type_name = type_name
      @variable_name = variable_name
      @location = location

      super "#{Location.to_string location}: Duplicated #{kind} variable name `#{variable_name}` in `#{type_name}`"
    end
  end

  class InstanceVariableDuplicationError < VariableDuplicationError
    def kind = 'instance'
  end

  class ClassInstanceVariableDuplicationError < VariableDuplicationError
    def kind = 'class instance'
  end

  class UnknownMethodAliasError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :original_name
    attr_reader :aliased_name
    attr_reader :location

    def initialize(type_name:, original_name:, aliased_name:, location:)
      @type_name = type_name
      @original_name = original_name
      @aliased_name = aliased_name
      @location = location

      super "#{Location.to_string location}: Unknown method alias name: #{original_name} => #{aliased_name} (#{type_name})"
    end
  end

  class SuperclassMismatchError < DefinitionError
    attr_reader :name
    attr_reader :entry

    def initialize(name:, entry:)
      @name = name
      @entry = entry
      super "#{Location.to_string entry.primary.decl.location}: Superclass mismatch: #{name}"
    end
  end

  class InvalidOverloadMethodError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :method_name
    attr_reader :kind
    attr_reader :members

    def initialize(type_name:, method_name:, kind:, members:)
      @type_name = type_name
      @method_name = method_name
      @kind = kind
      @members = members

      delimiter = case kind
                  when :instance
                    "#"
                  when :singleton
                    "."
                  end

      super "#{Location.to_string members[0].location}: Invalid method overloading: #{type_name}#{delimiter}#{method_name}"
    end

    def location
      members[0].location
    end
  end

  class GenericParameterMismatchError < LoadingError
    attr_reader :name
    attr_reader :decl

    def initialize(name:, decl:)
      @name = name
      @decl = decl
      super "#{Location.to_string decl.location}: Generic parameters mismatch: #{name}"
    end
  end

  class DuplicatedDeclarationError < LoadingError
    attr_reader :name
    attr_reader :decls

    def initialize(name, *decls)
      @name = name
      @decls = decls

      last_decl = decls.last or raise
      super "#{Location.to_string last_decl.location}: Duplicated declaration: #{name}"
    end
  end

  class InvalidVarianceAnnotationError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :param
    attr_reader :location

    def initialize(type_name:, param:, location:)
      @type_name = type_name
      @param = param
      @location = location

      super "#{Location.to_string location}: Type parameter variance error: #{param.name} is #{param.variance} but used as incompatible variance"
    end
  end

  class RecursiveAliasDefinitionError < DefinitionError
    include DetailedMessageable

    attr_reader :type
    attr_reader :defs

    def initialize(type:, defs:)
      @type = type
      @defs = defs

      super "#{Location.to_string location}: Recursive aliases in #{type}: #{defs.map(&:name).join(", ")}"
    end

    def location
      first_def = defs.first or raise
      original = first_def.original or raise
      original.location
    end
  end

  class MixinClassError < DefinitionError
    include DetailedMessageable

    attr_reader :type_name
    attr_reader :member

    def initialize(type_name:, member:)
      @type_name = type_name
      @member = member

      super "#{Location.to_string member.location}: Cannot #{mixin_name} a class `#{member.name}` in the definition of `#{type_name}`"
    end

    def location
      member.location
    end

    def self.check!(type_name:, env:, member:)
      if env.class_decl?(member.name)
        raise new(type_name: type_name, member: member)
      end
    end

    private

    def mixin_name
      case member
      when AST::Members::Prepend
        "prepend"
      when AST::Members::Include
        "include"
      when AST::Members::Extend
        "extend"
      else
        raise
      end
    end
  end

  class RecursiveTypeAliasError < BaseError
    include DetailedMessageable

    attr_reader :alias_names
    attr_reader :location

    def initialize(alias_names:, location:)
      @alias_names = alias_names
      @location = location

      super "#{Location.to_string location}: Recursive type alias definition found for: #{name}"
    end

    def name
      @alias_names.map(&:name).join(', ')
    end
  end

  class NonregularTypeAliasError < BaseError
    include DetailedMessageable

    attr_reader :diagnostic
    attr_reader :location

    def initialize(diagnostic:, location:)
      @diagnostic = diagnostic
      @location = location

      super "#{Location.to_string location}: Nonregular generic type alias is prohibited: #{diagnostic.type_name}, #{diagnostic.nonregular_type}"
    end
  end

  class CyclicTypeParameterBound < BaseError
    include DetailedMessageable

    attr_reader :params, :type_name, :method_name, :location

    def initialize(type_name:, method_name:, params:, location:)
      @type_name = type_name
      @method_name = method_name
      @params = params
      @location = location

      super "#{Location.to_string(location)}: Cyclic type parameter bound is prohibited"
    end
  end

  class InconsistentClassModuleAliasError < BaseError
    include DetailedMessageable

    attr_reader :alias_entry

    def initialize(entry)
      @alias_entry = entry

      expected_kind, actual_kind =
        case entry
        when Environment::ModuleAliasEntry
          ["module", "class"]
        when Environment::ClassAliasEntry
          ["class", "module"]
        end

      super "#{Location.to_string(entry.decl.location&.[](:old_name))}: A #{expected_kind} `#{entry.decl.new_name}` cannot be an alias of a #{actual_kind} `#{entry.decl.old_name}`"
    end

    def location
      @alias_entry.decl.location
    end
  end

  class CyclicClassAliasDefinitionError < BaseError
    include DetailedMessageable

    attr_reader :alias_entry

    def initialize(entry)
      @alias_entry = entry

      super "#{Location.to_string(entry.decl.location&.[](:old_name))}: A #{alias_entry.decl.new_name} is a cyclic definition"
    end

    def location
      @alias_entry.decl.location
    end
  end

  class WillSyntaxError < DefinitionError
    include DetailedMessageable

    attr_reader :location

    def initialize(message, location:)
      super "#{Location.to_string(location)}: #{message}"
      @location = location
    end
  end

  class TypeParamDefaultReferenceError < DefinitionError
    include DetailedMessageable

    attr_reader :type_param
    attr_reader :location

    def initialize(type_param, location:)
      super "#{Location.to_string(location)}: the default of #{type_param.name} cannot include optional type parameter"
      @location = location
      @type_param = type_param
    end

    def self.check!(type_params)
      if errors = AST::TypeParam.validate(type_params)
        error = errors[0] or raise
        error.default_type or raise
        raise new(error, location: error.default_type.location)
      end
    end
  end
end
