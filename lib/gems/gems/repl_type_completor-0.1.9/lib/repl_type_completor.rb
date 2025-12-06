# frozen_string_literal: true

require_relative 'repl_type_completor/version'
require_relative 'repl_type_completor/type_analyzer'
require_relative 'repl_type_completor/result'

module ReplTypeCompletor
  class << self
    attr_reader :last_completion_error

    def rbs_load_error
      Types.rbs_load_error
    end

    def rbs_load_started?
      Types.rbs_load_started?
    end

    def rbs_loaded?
      !!Types.rbs_builder
    end

    def load_rbs
      Types.load_rbs_builder unless rbs_loaded?
    end

    def preload_rbs
      Types.preload_rbs_builder
    end

    def analyze(code, binding:, filename: nil)
      verbose, $VERBOSE = $VERBOSE, nil
      result = analyze_code(code, binding)
      Result.new(result, binding, filename) if result
    rescue Exception => e
      handle_exception(e)
      nil
    ensure
      $VERBOSE = verbose
    end

    def handle_exception(e)
      @last_completion_error = e
    end

    def info
      require 'rbs'
      prism_info = "Prism: #{Prism::VERSION}"
      rbs_info = "RBS: #{RBS::VERSION}"
      if rbs_load_error
        rbs_info << " #{rbs_load_error.inspect}"
      elsif !rbs_load_started?
        rbs_info << ' signatures not loaded'
      elsif !rbs_loaded?
        rbs_info << ' signatures loading'
      end
      "ReplTypeCompletor: #{VERSION}, #{prism_info}, #{rbs_info}"
    end

    private

    def analyze_code(code, binding = Object::TOPLEVEL_BINDING)
      ast = Prism.parse(code, scopes: [binding.local_variables]).value
      *parents, target_node = find_target ast, code.bytesize
      return unless target_node

      calculate_scope = -> { TypeAnalyzer.calculate_target_type_scope(binding, parents, target_node).last }
      calculate_type_scope = ->(node) { TypeAnalyzer.calculate_target_type_scope binding, [*parents, target_node], node }

      case target_node
      when Prism::StringNode
        return unless target_node.closing&.empty?

        call_node, args_node = parents.last(2)
        return unless call_node.is_a?(Prism::CallNode) && call_node.receiver.nil?
        return unless args_node.is_a?(Prism::ArgumentsNode) && args_node.arguments.size == 1

        if call_node.name == :require || call_node.name == :require_relative
          [call_node.name, target_node.content]
        end
      when Prism::SymbolNode
        return unless !target_node.closing || target_node.closing.empty?

        name = target_node.value.to_s
        if parents.last.is_a? Prism::BlockArgumentNode # method(&:target)
          receiver_type, _scope = calculate_type_scope.call target_node
          [:call, name, receiver_type, false]
        else
          [:symbol, name] unless name.empty?
        end
      when Prism::CallNode, Prism::CallTargetNode
        return if target_node.is_a?(Prism::CallNode) && target_node.opening

        name = target_node.message.to_s
        return [:lvar_or_method, name, calculate_scope.call] if target_node.receiver.nil?

        self_call = target_node.receiver.is_a? Prism::SelfNode
        op = target_node.call_operator
        receiver_type, _scope = calculate_type_scope.call target_node.receiver
        receiver_type = receiver_type.nonnillable if op == '&.'
        [op == '::' ? :call_or_const : :call, name, receiver_type, self_call]
      when Prism::LocalVariableReadNode, Prism::LocalVariableTargetNode
        [:lvar_or_method, target_node.name.to_s, calculate_scope.call]
      when Prism::ItLocalVariableReadNode
        [:lvar_or_method, 'it', calculate_scope.call]
      when Prism::ConstantPathNode, Prism::ConstantPathTargetNode
        name = target_node.name.to_s
        if target_node.parent # A::B
          receiver, scope = calculate_type_scope.call(target_node.parent)
          [:const, name, receiver, scope]
        else # ::A
          scope = calculate_scope.call
          [:const, name, Types::SingletonType.new(Object), scope]
        end
      when Prism::ConstantReadNode, Prism::ConstantTargetNode
        [:const, target_node.name.to_s, nil, calculate_scope.call]
      when Prism::GlobalVariableReadNode, Prism::GlobalVariableTargetNode
        [:gvar, target_node.name.to_s, calculate_scope.call]
      when Prism::InstanceVariableReadNode, Prism::InstanceVariableTargetNode
        [:ivar, target_node.name.to_s, calculate_scope.call]
      when Prism::ClassVariableReadNode, Prism::ClassVariableTargetNode
        [:cvar, target_node.name.to_s, calculate_scope.call]
      end
    end

    def find_target(node, position)
      # Skip because location of these nodes gives location of whole block
      return if node.is_a?(Prism::NumberedParametersNode) || node.is_a?(Prism::ItParametersNode)

      node.compact_child_nodes.each do |n|
        match = find_target(n, position)
        next unless match

        match.unshift node
        return match
      end

      [node] if node.location.end_offset == position
    end
  end
end
