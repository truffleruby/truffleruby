# frozen_string_literal: true

module RBS
  class Sorter
    include RBS::AST

    attr_reader :path, :stdout

    def initialize(path, stdout: $stdout)
      @path = path
      @stdout = stdout
    end

    def run
      stdout.puts "Opening #{path}..."

      buffer = Buffer.new(name: path, content: path.read)
      _, _, sigs = Parser.parse_signature(buffer)

      sigs = sigs.map do |m|
        sort_decl m
      end

      stdout.puts "Writing #{path}..."
      path.open('w') do |out|
        writer = RBS::Writer.new(out: out)
        writer.write _ = sigs
      end
    end

    def sort_decl(decl)
      case decl
      when Declarations::Class, Declarations::Module, Declarations::Interface
        partitioned = {
          type_alias_decls: [],
          constant_decls: [],
          class_decls: [],
          mixin_includes: [],
          mixin_prepends: [],
          mixin_extends: [],
          class_variables: [],
          class_instance_variables: [],
          instance_variables: [],
          singleton_attributes: [],
          instance_attributes: [],
          module_functions: [],
          singleton_new_methods: [],
          public_singleton_methods: [],
          private_singleton_methods: [],
          instance_initialize_methods: [],
          public_instance_methods: [],
          private_instance_methods: [],
        } #: partitioned

        members = decl.members.map { |m| sort_decl m }
        decl = decl.update(members: _ = members)

        visibility_annotated_members = [] #: Array[member]

        decl.members.inject(:public) do |current_visibility, member| #$ AST::Members::visibility
          case member
          when Members::Public
            :public
          when Members::Private
            :private
          when Members::MethodDefinition, Members::AttrReader, Members::AttrWriter, Members::AttrAccessor
            visibility_annotated_members << member.update(visibility: member.visibility || current_visibility)
            current_visibility
          else
            visibility_annotated_members << member
            current_visibility
          end
        end

        visibility_annotated_members.each do |member|
          case member
          when Declarations::TypeAlias
            partitioned[:type_alias_decls] << member
          when Declarations::Constant
            partitioned[:constant_decls] << member
          when Declarations::Class, Declarations::Module, Declarations::Interface
            partitioned[:class_decls] << member
          when Members::Include
            partitioned[:mixin_includes] << member
          when Members::Prepend
            partitioned[:mixin_prepends] << member
          when Members::Extend
            partitioned[:mixin_extends] << member
          when Members::ClassVariable
            partitioned[:class_variables] << member
          when Members::ClassInstanceVariable
            partitioned[:class_instance_variables] << member
          when Members::InstanceVariable
            partitioned[:instance_variables] << member
          when Members::AttrAccessor, Members::AttrWriter, Members::AttrReader
            if member.kind == :singleton
              partitioned[:singleton_attributes] << member.update(visibility: nil)
            else
              partitioned[:instance_attributes] << member.update(visibility: nil)
            end
          when Members::MethodDefinition
            case member.kind
            when :singleton_instance
              partitioned[:module_functions] << member.update(visibility: nil)
            when :singleton
              if member.name == :new
                partitioned[:singleton_new_methods] << member.update(visibility: nil)
              elsif member.visibility == :public
                partitioned[:public_singleton_methods] << member.update(visibility: nil)
              else
                partitioned[:private_singleton_methods] << member.update(visibility: nil)
              end
            else
              if member.name == :initialize
                partitioned[:instance_initialize_methods] << member.update(visibility: nil)
              elsif member.visibility == :public
                partitioned[:public_instance_methods] << member.update(visibility: nil)
              else
                partitioned[:private_instance_methods] << member.update(visibility: nil)
              end
            end
          when Members::Alias
            if member.singleton?
              partitioned[:public_singleton_methods] << member
            else
              partitioned[:public_instance_methods] << member
            end
          when Members::Public, Members::Private
            raise
          else
            partitioned[:other_decls] << member
          end
        end

        partitioned[:type_alias_decls].sort_by! {|decl| decl.name.to_s }
        partitioned[:constant_decls].sort_by! {|decl| decl.name.to_s }
        partitioned[:class_decls].sort_by! {|decl| decl.name.to_s }
        partitioned[:mixin_includes].sort_by! {|decl| decl.name.to_s }
        partitioned[:mixin_prepends].sort_by! {|decl| decl.name.to_s }
        partitioned[:mixin_extends].sort_by! {|decl| decl.name.to_s }
        partitioned[:class_variables].sort_by! {|decl| decl.name.to_s }
        partitioned[:class_instance_variables].sort_by! {|decl| decl.name.to_s }
        partitioned[:instance_variables].sort_by! {|decl| decl.name.to_s }
        partitioned[:singleton_attributes].sort_by! {|decl| decl.name.to_s }
        partitioned[:instance_attributes].sort_by! {|decl| decl.name.to_s }
        partitioned[:module_functions].sort_by! {|decl| decl.name.to_s }
        partitioned[:public_singleton_methods].sort_by! {|decl| decl.is_a?(Members::MethodDefinition) ? decl.name.to_s : decl.new_name.to_s }
        partitioned[:private_singleton_methods].sort_by! {|decl| decl.name.to_s }
        partitioned[:public_instance_methods].sort_by! {|decl| decl.is_a?(Members::MethodDefinition) ? decl.name.to_s : decl.new_name.to_s }
        partitioned[:private_instance_methods].sort_by! {|decl| decl.name.to_s }

        members = [] #: Array[member]
        members.push(*partitioned[:type_alias_decls])
        members.push(*partitioned[:constant_decls])
        members.push(*partitioned[:class_decls])
        members.push(*partitioned[:mixin_includes])
        members.push(*partitioned[:mixin_prepends])
        members.push(*partitioned[:mixin_extends])
        members.push(*partitioned[:class_variables])
        members.push(*partitioned[:class_instance_variables])
        members.push(*partitioned[:instance_variables])
        members.push(*partitioned[:module_functions])
        members.push(*partitioned[:singleton_attributes])

        current_visibility = :public #: AST::Members::visibility

        members.push(*partitioned[:singleton_new_methods])
        members.push(*partitioned[:public_singleton_methods])

        if !partitioned[:private_singleton_methods].empty?
          current_visibility = :private
          members.push Members::Private.new(location: nil)
        end
        members.push(*partitioned[:private_singleton_methods])

        if current_visibility == :private && !partitioned[:public_instance_methods].empty?
          current_visibility = :public
          members.push Members::Public.new(location: nil)
        end
        members.push(*partitioned[:instance_attributes])
        members.push(*partitioned[:instance_initialize_methods])
        members.push(*partitioned[:public_instance_methods])

        if current_visibility == :public && !partitioned[:private_instance_methods].empty?
          current_visibility = :private
          members.push Members::Private.new(location: nil)
        end
        members.push(*partitioned[:private_instance_methods])

        members.push(*partitioned[:other_decls])

        decl.update(members: _ = members)
      else
        decl
      end
    end
  end
end
