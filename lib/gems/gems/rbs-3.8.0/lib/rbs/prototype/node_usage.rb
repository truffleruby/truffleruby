# frozen_string_literal: true

module RBS
  module Prototype
    class NodeUsage
      include Helpers

      attr_reader :conditional_nodes

      def initialize(node)
        @node = node
        @conditional_nodes = Set[].compare_by_identity

        calculate(node, conditional: false)
      end

      def each_conditional_node(&block)
        if block
          conditional_nodes.each(&block)
        else
          conditional_nodes.each
        end
      end

      def calculate(node, conditional:)
        if conditional
          conditional_nodes << node
        end

        case node.type
        when :IF, :UNLESS
          cond_node, true_node, false_node = node.children
          calculate(cond_node, conditional: true)
          calculate(true_node, conditional: conditional) if true_node
          calculate(false_node, conditional: conditional) if false_node
        when :AND, :OR
          left, right = node.children
          calculate(left, conditional: true)
          calculate(right, conditional: conditional)
        when :QCALL
          receiver, _, args = node.children
          calculate(receiver, conditional: true)
          calculate(args, conditional: false) if args
        when :WHILE
          cond, body = node.children
          calculate(cond, conditional: true)
          calculate(body, conditional: false) if body
        when :OP_ASGN_OR, :OP_ASGN_AND
          var, _, asgn = node.children
          calculate(var, conditional: true)
          calculate(asgn, conditional: conditional)
        when :LASGN, :IASGN, :GASGN
          _, lhs = node.children
          calculate(lhs, conditional: conditional) if lhs
        when :MASGN
          lhs, _ = node.children
          calculate(lhs, conditional: conditional)
        when :CDECL
          if node.children.size == 2
            _, lhs = node.children
            calculate(lhs, conditional: conditional)
          else
            const, _, lhs = node.children
            calculate(const, conditional: false)
            calculate(lhs, conditional: conditional)
          end
        when :SCOPE
          _, _, body = node.children
          calculate(body, conditional: conditional)
        when :CASE2
          _, *branches = node.children
          branches.each do |branch|
            if branch.type == :WHEN
              list, body = branch.children
              list.children.each do |child|
                if child
                  calculate(child, conditional: true)
                end
              end
              calculate(body, conditional: conditional)
            else
              calculate(branch, conditional: conditional)
            end
          end
        when :BLOCK
          *nodes, last = node.children
          nodes.each do |no|
            calculate(no, conditional: false)
          end
          calculate(last, conditional: conditional) if last
        else
          each_child(node) do |child|
            calculate(child, conditional: false)
          end
        end
      end
    end
  end
end
