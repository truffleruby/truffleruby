# frozen_string_literal: true

require_relative 'helpers'

module RBS
  module Prototype
    class Runtime
      class ValueObjectBase
        include Helpers

        def initialize(target_class)
          @target_class = target_class
        end

        def build_decl
          decl = AST::Declarations::Class.new(
            name: to_type_name(only_name(@target_class)),
            type_params: [],
            super_class: build_super_class,
            members: [],
            annotations: [],
            location: nil,
            comment: nil
          )

          add_decl_members(decl)

          decl
        end

        private

        # def self.members: () -> [ :foo, :bar ]
        # def members: () -> [ :foo, :bar ]
        def build_s_members
          (
            [:singleton, :instance] #: Array[AST::Members::MethodDefinition::kind]
          ).map do |kind|
            AST::Members::MethodDefinition.new(
              name: :members,
              overloads: [
                AST::Members::MethodDefinition::Overload.new(
                  annotations: [],
                  method_type: MethodType.new(
                    type: Types::Function.empty(
                      Types::Tuple.new(
                        types: @target_class.members.map do |member|
                          if member.to_s.ascii_only?
                            Types::Literal.new(literal: member, location: nil)
                          else
                            BuiltinNames::Symbol.instance_type
                          end
                        end,
                        location: nil
                      )
                    ),
                    type_params: [],
                    block: nil,
                    location: nil,
                  )
                )
              ],
              kind: kind,
              location: nil,
              comment: nil,
              annotations: [],
              overloading: false,
              visibility: nil
            )
          end
        end

        # attr_accessor foo: untyped
        def build_member_accessors(ast_members_class)
          @target_class.members.map do |member|
            ast_members_class.new(
              name: member,
              ivar_name: nil,
              type: untyped,
              kind: :instance,
              location: nil,
              comment: nil,
              annotations: (
                [] #: Array[AST::Annotation]
              )
            )
          end
        end
      end

      class StructGenerator < ValueObjectBase
        def self.generatable?(target)
          return false unless target < Struct
          # Avoid direct inherited class like `class Option < Struct`
          return false unless target.respond_to?(:members)

          true
        end

        private

        CAN_CALL_KEYWORD_INIT_P = Struct.new(:tmp).respond_to?(:keyword_init?)

        def build_super_class
          AST::Declarations::Class::Super.new(name: TypeName.parse("::Struct"), args: [untyped], location: nil)
        end

        def add_decl_members(decl)
          decl.members.concat build_s_new
          decl.members.concat build_s_keyword_init_p
          decl.members.concat build_s_members
          decl.members.concat build_member_accessors(AST::Members::AttrAccessor)
        end

        # def self.new: (?untyped foo, ?untyped bar) -> instance
        #             | (?foo: untyped, ?bar: untyped) -> instance
        def build_s_new
          [:new, :[]].map do |name|
            new_overloads = [] #: Array[AST::Members::MethodDefinition::Overload]

            if CAN_CALL_KEYWORD_INIT_P
              case @target_class.keyword_init?
              when false
                new_overloads << build_overload_for_positional_arguments
              when true
                new_overloads << build_overload_for_keyword_arguments
              when nil
                new_overloads << build_overload_for_positional_arguments
                new_overloads << build_overload_for_keyword_arguments
              else
                raise
              end
            else
              new_overloads << build_overload_for_positional_arguments
              new_overloads << build_overload_for_keyword_arguments
            end

            AST::Members::MethodDefinition.new(
              name: name,
              overloads: new_overloads,
              kind: :singleton,
              location: nil,
              comment: nil,
              annotations: [],
              overloading: false,
              visibility: nil
            )
          end
        end

        def build_overload_for_positional_arguments
          AST::Members::MethodDefinition::Overload.new(
            annotations: [],
            method_type: MethodType.new(
              type: Types::Function.empty(Types::Bases::Instance.new(location: nil)).update(
                optional_positionals: @target_class.members.map { |m| Types::Function::Param.new(name: m, type: untyped) },
              ),
              type_params: [],
              block: nil,
              location: nil,
            )
          )
        end

        def build_overload_for_keyword_arguments
          AST::Members::MethodDefinition::Overload.new(
            annotations: [],
            method_type: MethodType.new(
              type: Types::Function.empty(Types::Bases::Instance.new(location: nil)).update(
                optional_keywords: @target_class.members.to_h { |m| [m, Types::Function::Param.new(name: nil, type: untyped)] },
              ),
              type_params: [],
              block: nil,
              location: nil,
            )
          )
        end

        # def self.keyword_init?: () -> bool?
        def build_s_keyword_init_p
          return [] unless CAN_CALL_KEYWORD_INIT_P

          return_type = @target_class.keyword_init?.nil? \
                      ? Types::Bases::Nil.new(location: nil)
                      : Types::Literal.new(literal: @target_class.keyword_init?, location: nil)
          type = Types::Function.empty(return_type)

          [
            AST::Members::MethodDefinition.new(
              name: :keyword_init?,
              overloads: [
                AST::Members::MethodDefinition::Overload.new(
                  annotations: [],
                  method_type: MethodType.new(
                    type: type,
                    type_params: [],
                    block: nil,
                    location: nil,
                  )
                )
              ],
              kind: :singleton,
              location: nil,
              comment: nil,
              annotations: [],
              overloading: false,
              visibility: nil
            )
          ]
        end
      end

      class DataGenerator < ValueObjectBase
        def self.generatable?(target)
          return false unless RUBY_VERSION >= '3.2'
          return false unless target < Data
          # Avoid direct inherited class like `class Option < Data`
          return false unless target.respond_to?(:members)

          true
        end

        private

        def build_super_class
          AST::Declarations::Class::Super.new(name: TypeName.parse("::Data"), args: [], location: nil)
        end

        def add_decl_members(decl)
          decl.members.concat build_s_new
          decl.members.concat build_s_members
          decl.members.concat build_member_accessors(AST::Members::AttrReader)
        end

        # def self.new: (untyped foo, untyped bar) -> instance
        #             | (foo: untyped, bar: untyped) -> instance
        def build_s_new
          [:new, :[]].map do |name|
            new_overloads = [] #: Array[AST::Members::MethodDefinition::Overload]

            new_overloads << AST::Members::MethodDefinition::Overload.new(
              annotations: [],
              method_type: MethodType.new(
                type: Types::Function.empty(Types::Bases::Instance.new(location: nil)).update(
                  required_positionals: @target_class.members.map { |m| Types::Function::Param.new(name: m, type: untyped) },
                ),
                type_params: [],
                block: nil,
                location: nil,
              )
            )
            new_overloads << AST::Members::MethodDefinition::Overload.new(
              annotations: [],
              method_type: MethodType.new(
                type: Types::Function.empty(Types::Bases::Instance.new(location: nil)).update(
                  required_keywords: @target_class.members.to_h { |m| [m, Types::Function::Param.new(name: nil, type: untyped)] },
                ),
                type_params: [],
                block: nil,
                location: nil,
              )
            )

            AST::Members::MethodDefinition.new(
              name: name,
              overloads: new_overloads,
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
    end
  end
end
