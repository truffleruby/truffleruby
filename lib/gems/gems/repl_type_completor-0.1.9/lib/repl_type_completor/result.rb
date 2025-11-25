# frozen_string_literal: true

require_relative 'require_paths'

module ReplTypeCompletor
  class Result
    OPERATOR_METHODS = %w[! != !~ % & * ** + +@ - -@ / < << <= <=> == === =~ > >= >> [] []= ^ ` | ~]
    HIDDEN_METHODS = [
      # defined by RBS, should be hidden
      'Namespace', 'TypeName',
      # operator methods does not need to be completed
      *OPERATOR_METHODS
    ]
    RESERVED_WORDS = %w[
      __ENCODING__ __LINE__ __FILE__
      BEGIN END
      alias and
      begin break
      case class
      def defined? do
      else elsif end ensure
      false for
      if in
      module
      next nil not
      or
      redo rescue retry return
      self super
      then true
      undef unless until
      when while
      yield
    ]

    def initialize(analyze_result, binding, source_file)
      @analyze_result = analyze_result
      @binding = binding
      @source_file = source_file
    end

    def completion_candidates
      verbose, $VERBOSE = $VERBOSE, nil
      candidates = case @analyze_result
      in [:require, name]
        RequirePaths.require_completions(name)
      in [:require_relative, name]
        RequirePaths.require_relative_completions(name, @source_file)
      in [:call_or_const, name, type, self_call]
        ((self_call ? type.all_methods : type.methods).map(&:to_s) - HIDDEN_METHODS) | type.constants
      in [:const, name, type, scope]
        if type
          scope_constants = type.types.flat_map do |t|
            scope.table_module_constants(t.module_or_class) if t.is_a?(Types::SingletonType)
          end
          (scope_constants.compact | type.constants.map(&:to_s)).sort
        else
          scope.constants.sort | RESERVED_WORDS
        end
      in [:ivar, name, scope]
        ivars = scope.instance_variables.sort
        name == '@' ? ivars + scope.class_variables.sort : ivars
      in [:cvar, name, scope]
        scope.class_variables
      in [:gvar, name, scope]
        scope.global_variables
      in [:symbol, name]
        filter_symbol_candidates(Symbol.all_symbols, name, limit: 100)
      in [:call, name, type, self_call]
        (self_call ? type.all_methods : type.methods).map(&:to_s) - HIDDEN_METHODS
      in [:lvar_or_method, name, scope]
        scope.self_type.all_methods.map(&:to_s) | scope.local_variables | RESERVED_WORDS
      else
        []
      end
      candidates.select { _1.start_with?(name) }.map { _1[name.size..] }
    rescue Exception => e
      ReplTypeCompletor.handle_exception(e)
      []
    ensure
      $VERBOSE = verbose
    end

    def doc_namespace(matched)
      verbose, $VERBOSE = $VERBOSE, nil
      case @analyze_result
      in [:call_or_const, prefix, type, _self_call]
        call_or_const_doc type, prefix + matched
      in [:const, prefix, type, scope]
        if type
          call_or_const_doc type, prefix + matched
        else
          value_doc scope[prefix + matched]
        end
      in [:gvar, prefix, scope]
        value_doc scope[prefix + matched]
      in [:ivar, prefix, scope]
        value_doc scope[prefix + matched]
      in [:cvar, prefix, scope]
        value_doc scope[prefix + matched]
      in [:call, prefix, type, _self_call]
        method_doc type, prefix + matched
      in [:lvar_or_method, prefix, scope]
        if scope.local_variables.include?(prefix + matched)
          value_doc scope[prefix + matched]
        else
          method_doc scope.self_type, prefix + matched
        end
      else
      end
    rescue Exception => e
      ReplTypeCompletor.handle_exception(e)
      nil
    ensure
      $VERBOSE = verbose
    end

    private

    def filter_symbol_candidates(symbols, prefix, limit:)
      sym_prefix = ":#{prefix}"
      candidates = symbols.filter_map do |s|
        next unless s.start_with?(prefix) # Fast and inaccurate check before calling inspect

        inspect = s.inspect
        inspect[1..] if inspect.start_with?(sym_prefix) # Reject `:"a b"` when completing `:a`
      rescue EncodingError
        # ignore
      end

      if candidates.size > limit
        # min(n) + max(n) is faster than sort and slice
        candidates.min(limit - limit / 2) + candidates.max(limit / 2).reverse
      else
        candidates.sort
      end
    end

    def method_doc(type, name)
      type = type.types.find { _1.all_methods.include? name.to_sym }
      case type
      when Types::SingletonType
        "#{Types.class_name_of(type.module_or_class)}.#{name}"
      when Types::InstanceType
        "#{Types.class_name_of(type.klass)}##{name}"
      end
    end

    def call_or_const_doc(type, name)
      if name =~ /\A[A-Z]/
        type = type.types.grep(Types::SingletonType).find { _1.module_or_class.const_defined?(name) }
        type.module_or_class == Object ? name : "#{Types.class_name_of(type.module_or_class)}::#{name}" if type
      else
        method_doc(type, name)
      end
    end

    def value_doc(type)
      return unless type
      type.types.each do |t|
        case t
        when Types::SingletonType
          return Types.class_name_of(t.module_or_class)
        when Types::InstanceType
          return Types.class_name_of(t.klass)
        end
      end
      nil
    end
  end
end
