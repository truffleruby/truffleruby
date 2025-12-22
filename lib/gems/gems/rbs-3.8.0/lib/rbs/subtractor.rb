# frozen_string_literal: true

module RBS
  class Subtractor
    def initialize(minuend, subtrahend)
      @minuend = minuend
      @subtrahend = subtrahend
    end

    def call(minuend = @minuend, context: nil)
      minuend.map do |decl|
        case decl
        when AST::Declarations::Constant
          name = absolute_typename(decl.name, context: context)
          decl unless @subtrahend.constant_name?(name)
        when AST::Declarations::Interface
          name = absolute_typename(decl.name, context: context)
          decl unless @subtrahend.interface_name?(name)
        when AST::Declarations::Class, AST::Declarations::Module
          name = absolute_typename(decl.name, context: context)
          case
          when @subtrahend.class_decl?(name) && decl.is_a?(AST::Declarations::Class)
            filter_members(decl, context: context)
          when @subtrahend.module_decl?(name) && decl.is_a?(AST::Declarations::Module)
            filter_members(decl, context: context)
          when @subtrahend.constant_name?(name)
            nil
          else
            decl
          end
        when AST::Declarations::Global
          decl unless @subtrahend.global_decls[decl.name]
        when AST::Declarations::TypeAlias
          name = absolute_typename(decl.name, context: context)
          decl unless @subtrahend.type_alias_decls[name]
        when AST::Declarations::ClassAlias
          name = absolute_typename(decl.new_name, context: context)
          decl unless @subtrahend.class_alias?(name) || @subtrahend.class_decl?(name)
        when AST::Declarations::ModuleAlias
          name = absolute_typename(decl.new_name, context: context)
          decl unless @subtrahend.module_alias?(name) || @subtrahend.module_decl?(name)
        else
          raise "unknwon decl: #{(_ = decl).class}"
        end
      end.compact
    end

    private def filter_members(decl, context:)
      owner = absolute_typename(decl.name, context: context)

      context = _ = [context, decl.name]
      children = call(decl.each_decl.to_a, context: context) +
        decl.each_member.reject { |m| member_exist?(owner, m, context: context) }
      children = filter_redundunt_access_modifiers(children)
      return nil if children.empty?

      update_decl(decl, members: children)
    end

    private def member_exist?(owner, member, context:)
      case member
      when AST::Members::MethodDefinition
        method_exist?(owner, member.name, member.kind)
      when AST::Members::Alias
        method_exist?(owner, member.new_name, member.kind)
      when AST::Members::AttrReader
        method_exist?(owner, member.name, member.kind)
      when AST::Members::AttrWriter
        method_exist?(owner, :"#{member.name}=", member.kind)
      when AST::Members::AttrAccessor
        # TODO: It unexpectedly removes attr_accessor even if either reader or writer does not exist in the subtrahend.
        method_exist?(owner, member.name, member.kind) || method_exist?(owner, :"#{member.name}=", member.kind)
      when AST::Members::InstanceVariable
        ivar_exist?(owner, member.name, :instance)
      when AST::Members::ClassInstanceVariable
        ivar_exist?(owner, member.name, :singleton)
      when AST::Members::ClassVariable
        cvar_exist?(owner, member.name)
      when AST::Members::Include, AST::Members::Extend, AST::Members::Prepend
        mixin_exist?(owner, member, context: context)
      when AST::Members::Public, AST::Members::Private
        # They should not be removed even if the subtrahend has them.
        false
      else
        raise "unknown member: #{(_ = member).class}"
      end
    end

    private def method_exist?(owner, method_name, kind)
      each_member(owner).any? do |m|
        case m
        when AST::Members::MethodDefinition
          m.name == method_name && m.kind == kind
        when AST::Members::Alias
          m.new_name == method_name && m.kind == kind
        when AST::Members::AttrReader
          m.name == method_name && m.kind == kind
        when AST::Members::AttrWriter
          :"#{m.name}=" == method_name && m.kind == kind
        when AST::Members::AttrAccessor
          (m.name == method_name || :"#{m.name}=" == method_name) && m.kind == kind
        end
      end
    end

    private def ivar_exist?(owner, name, kind)
      each_member(owner).any? do |m|
        case m
        when AST::Members::InstanceVariable
          m.name == name
        when AST::Members::Attribute
          ivar_name = m.ivar_name == false ? nil : m.ivar_name || :"@#{m.name}"
          ivar_name == name && m.kind == kind
        end
      end
    end

    private def cvar_exist?(owner, name)
      each_member(owner).any? do |m|
        case m
        when AST::Members::ClassVariable
          m.name == name
        end
      end
    end

    private def each_member(owner, &block)
      return enum_for((__method__ or raise), owner) unless block

      entry = @subtrahend.class_decls[owner]
      return unless entry
      decls = entry.decls.map { |d| d.decl }

      decls.each { |d| d.members.each { |m| block.call(m) } }
    end

    private def mixin_exist?(owner, mixin, context:)
      candidates = typename_candidates(mixin.name, context: context)
      each_member(owner).any? do |m|
        case m
        when mixin.class
          # @type var m: AST::Members::Include | AST::Members::Extend | AST::Members::Prepend
          candidates.include?(m.name)
        end
      end
    end

    private def filter_redundunt_access_modifiers(decls)
      decls = decls.dup
      decls.pop while access_modifier?(decls.last)
      decls = decls.map.with_index do |decl, i|
        if access_modifier?(decl) && access_modifier?(decls[i + 1])
          nil
        else
          decl
        end
      end.compact
    end

    private def access_modifier?(decl)
      decl.is_a?(AST::Members::Public) || decl.is_a?(AST::Members::Private)
    end

    private def update_decl(decl, members:)
      case decl
      when AST::Declarations::Class
        decl.class.new(name: decl.name, type_params: decl.type_params, super_class: decl.super_class,
                        annotations: decl.annotations, location: decl.location, comment: decl.comment,
                        members: members)
      when AST::Declarations::Module
        decl.class.new(name: decl.name, type_params: decl.type_params, self_types: decl.self_types,
                        annotations: decl.annotations, location: decl.location, comment: decl.comment,
                        members: members)
      end
    end

    private def absolute_typename(name, context:)
      while context
        ns = context[1] or raise
        name = name.with_prefix(ns.to_namespace)
        context = _ = context[0]
      end
      name.absolute!
    end

    private def typename_candidates(name, context:)
      ret = [name.absolute!, name.relative!]
      return ret if name.absolute?

      while context
        ns = context[1] or raise
        name = name.with_prefix(ns.to_namespace)
        ret.concat [name.absolute!, name.relative!]

        context = _ = context[0]
      end

      ret
    end
  end
end
