# frozen_string_literal: true

module RBS
  module AST
    # The Visitor class implements the Visitor pattern for traversing the RBS Abstract Syntax Tree (AST).
    #
    # It provides methods to visit each type of node in the AST, allowing for custom processing of each node type.
    #
    # This class is designed to be subclassed, with specific visit methods overridden to implement custom behavior for
    # different node types.
    #
    # Example usage:
    #
    # ~~~rb
    # class MyVisitor < RBS::AST::Visitor
    #   def visit_declaration_class(node)
    #     puts "Visiting class: #{node.name}"
    #
    #     super # call `super` to run the default visiting behavior
    #   end
    # end
    #
    # visitor = MyVisitor.new
    # visitor.visit(ast_node)
    # ~~~
    class Visitor
      def visit(node)
        case node
        when Declarations::Global
          visit_declaration_global(node)
        when Declarations::Class
          visit_declaration_class(node)
        when Declarations::Module
          visit_declaration_module(node)
        when Declarations::Constant
          visit_declaration_constant(node)
        when Declarations::TypeAlias
          visit_declaration_type_alias(node)
        when Declarations::Interface
          visit_declaration_interface(node)
        when Members::Alias
          visit_member_alias(node)
        when Members::ClassInstanceVariable
          visit_member_class_instance_variable(node)
        when Members::ClassVariable
          visit_member_class_variable(node)
        when Members::InstanceVariable
          visit_member_instance_variable(node)
        when Members::Private
          visit_member_private(node)
        when Members::Public
          visit_member_public(node)
        when Members::MethodDefinition
          visit_member_method_definition(node)
        when Members::AttrReader
          visit_member_attr_reader(node)
        when Members::AttrWriter
          visit_member_attr_writer(node)
        when Members::AttrAccessor
          visit_member_attr_accessor(node)
        when Members::Include
          visit_member_include(node)
        when Members::Prepend
          visit_member_prepend(node)
        when Members::Extend
          visit_member_extend(node)
        end
      end

      def visit_all(nodes)
        nodes.each do |node|
          visit(node)
        end
      end

      def visit_declaration_global(node)
      end

      def visit_declaration_class(node)
        visit_all(node.members)
      end

      def visit_declaration_module(node)
        visit_all(node.members)
      end

      def visit_declaration_constant(node)
      end

      def visit_declaration_type_alias(node)
      end

      def visit_declaration_interface(node)
        visit_all(node.members)
      end

      def visit_member_alias(node)
      end

      def visit_member_class_instance_variable(node)
      end

      def visit_member_class_variable(node)
      end

      def visit_member_instance_variable(node)
      end

      def visit_member_private(node)
      end

      def visit_member_public(node)
      end

      def visit_member_method_definition(node)
      end

      def visit_member_attr_reader(node)
      end

      def visit_member_attr_writer(node)
      end

      def visit_member_attr_accessor(node)
      end

      def visit_member_include(node)
      end

      def visit_member_prepend(node)
      end

      def visit_member_extend(node)
      end
    end
  end
end
