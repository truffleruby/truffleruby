# frozen_string_literal: true

module RBS
  module Test
    module Errors
      ArgumentTypeError =
        Struct.new(:klass, :method_name, :method_type, :param, :value, keyword_init: true)
      BlockArgumentTypeError =
        Struct.new(:klass, :method_name, :method_type, :param, :value, keyword_init: true)
      ArgumentError =
        Struct.new(:klass, :method_name, :method_type, keyword_init: true)
      BlockArgumentError =
        Struct.new(:klass, :method_name, :method_type, keyword_init: true)
      ReturnTypeError =
        Struct.new(:klass, :method_name, :method_type, :type, :value, keyword_init: true)
      BlockReturnTypeError =
        Struct.new(:klass, :method_name, :method_type, :type, :value, keyword_init: true)

      UnexpectedBlockError = Struct.new(:klass, :method_name, :method_type, keyword_init: true)
      MissingBlockError = Struct.new(:klass, :method_name, :method_type, keyword_init: true)

      UnresolvedOverloadingError = Struct.new(:klass, :method_name, :method_types, keyword_init: true)

      def self.format_param(param)
        if param.name
          "`#{param.type}` (#{param.name})"
        else
          "`#{param.type}`"
        end
      end

      RESPOND_TO = ::Kernel.instance_method :respond_to?
      private_constant :RESPOND_TO

      def self.inspect_(obj)
        if RESPOND_TO.bind_call(obj, :inspect)
          obj.inspect
        else
          Test::INSPECT.bind(obj).call     # For the case inspect is not defined (like BasicObject)
        end
      end

      def self.method_tag(error)
        if error.klass.singleton_class?
          name = inspect_(error.klass).sub(/\A#<Class:(.*)>\z/, '\1')
          method_name = ".#{error.method_name}"
        else
          name = error.klass.name
          method_name = "##{error.method_name}"
        end
        "[#{name}#{method_name}]"
      end

      def self.to_string(error)
        case error
        when ArgumentTypeError
          "#{method_tag(error)} ArgumentTypeError: expected #{format_param error.param} but given `#{inspect_(error.value)}`"
        when BlockArgumentTypeError
          "#{method_tag(error)} BlockArgumentTypeError: expected #{format_param error.param} but given `#{inspect_(error.value)}`"
        when ArgumentError
          "#{method_tag(error)} ArgumentError: expected method type #{error.method_type}"
        when BlockArgumentError
          "#{method_tag(error)} BlockArgumentError: expected method type #{error.method_type}"
        when ReturnTypeError
          "#{method_tag(error)} ReturnTypeError: expected `#{error.type}` but returns `#{inspect_(error.value)}`"
        when BlockReturnTypeError
          "#{method_tag(error)} BlockReturnTypeError: expected `#{error.type}` but returns `#{inspect_(error.value)}`"
        when UnexpectedBlockError
          "#{method_tag(error)} UnexpectedBlockError: unexpected block is given for `#{error.method_type}`"
        when MissingBlockError
          "#{method_tag(error)} MissingBlockError: required block is missing for `#{error.method_type}`"
        when UnresolvedOverloadingError
          "#{method_tag(error)} UnresolvedOverloadingError: couldn't find a suitable overloading"
        else
          raise "Unexpected error: #{inspect_(error)}"
        end
      end
    end
  end
end
