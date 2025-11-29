# frozen_string_literal: true

module RBS
  class CLI
    class Validate
      class Errors
        def initialize(limit:, exit_error:)
          @limit = limit
          @exit_error = exit_error
          @errors = []
          @has_syntax_error = false
        end

        def add(error)
          if error.instance_of?(WillSyntaxError)
            RBS.logger.warn(build_message(error))
            @has_syntax_error = true
          else
            @errors << error
          end
          finish if @limit == 1
        end

        def finish
          if @errors.empty?
            if @exit_error && @has_syntax_error
              exit 1
            else
              # success
            end
          else
            @errors.each do |error|
              RBS.logger.error(build_message(error))
            end
            exit 1
          end
        end

        private

        def build_message(error)
          if error.respond_to?(:detailed_message)
            highlight = RBS.logger_output ? RBS.logger_output.tty? : true
            error.detailed_message(highlight: highlight)
          else
            "#{error.message} (#{error.class})"
          end
        end
      end

      def initialize(args:, options:)
        loader = options.loader()
        @env = Environment.from_loader(loader).resolve_type_names
        @builder = DefinitionBuilder.new(env: @env)
        @validator = Validator.new(env: @env)
        exit_error = false
        limit = nil #: Integer?
        OptionParser.new do |opts|
          opts.banner = <<EOU
Usage: rbs validate

Validate RBS files. It ensures the type names in RBS files are present and the type applications have correct arity.

Examples:

  $ rbs validate
EOU

          opts.on("--silent", "This option has been deprecated and does nothing.") do
            RBS.print_warning { "`--silent` option is deprecated because it's silent by default. You can use --log-level option of rbs command to display more information." }
          end
          opts.on("--[no-]exit-error-on-syntax-error", "exit(1) if syntax error is detected") {|bool|
            exit_error = bool
          }
          opts.on("--fail-fast", "Exit immediately as soon as a validation error is found.") do |arg|
            limit = 1
          end
        end.parse!(args)

        @errors = Errors.new(limit: limit, exit_error: exit_error)
      end

      def run
        validate_class_module_definition
        validate_class_module_alias_definition
        validate_interface
        validate_constant
        validate_global
        validate_type_alias

        @errors.finish
      end

      private

      def validate_class_module_definition
        @env.class_decls.each do |name, entry|
          RBS.logger.info "Validating class/module definition: `#{name}`..."
          @builder.build_instance(name).each_type do |type|
            @validator.validate_type type, context: nil
          rescue BaseError => error
            @errors.add(error)
          end
          @builder.build_singleton(name).each_type do |type|
            @validator.validate_type type, context: nil
          rescue BaseError => error
            @errors.add(error)
          end

          case entry
          when Environment::ClassEntry
            entry.decls.each do |decl|
              if super_class = decl.decl.super_class
                super_class.args.each do |arg|
                  void_type_context_validator(arg, true)
                  no_self_type_validator(arg)
                  no_classish_type_validator(arg)
                  @validator.validate_type(arg, context: nil)
                end
              end
            end
          when Environment::ModuleEntry
            entry.decls.each do |decl|
              decl.decl.self_types.each do |self_type|
                self_type.args.each do |arg|
                  void_type_context_validator(arg, true)
                  no_self_type_validator(arg)
                  no_classish_type_validator(arg)
                  @validator.validate_type(arg, context: nil)
                end

                self_params =
                  if self_type.name.class?
                    @env.normalized_module_entry(self_type.name)&.type_params
                  else
                    @env.interface_decls[self_type.name]&.decl&.type_params
                  end

                if self_params
                  InvalidTypeApplicationError.check!(type_name: self_type.name, params: self_params, args: self_type.args, location: self_type.location)
                end
              end
            end
          end

          d = entry.primary.decl

          @validator.validate_type_params(
            d.type_params,
            type_name: name,
            location: d.location&.aref(:type_params)
          )

          d.type_params.each do |param|
            if ub = param.upper_bound_type
              void_type_context_validator(ub)
              no_self_type_validator(ub)
              no_classish_type_validator(ub)
              @validator.validate_type(ub, context: nil)
            end

            if dt = param.default_type
              void_type_context_validator(dt, true)
              no_self_type_validator(dt)
              no_classish_type_validator(dt)
              @validator.validate_type(dt, context: nil)
            end
          end

          TypeParamDefaultReferenceError.check!(d.type_params)

          entry.decls.each do |d|
            d.decl.each_member do |member|
              case member
              when AST::Members::MethodDefinition
                @validator.validate_method_definition(member, type_name: name)
                member.overloads.each do |ov|
                  void_type_context_validator(ov.method_type)
                end
              when AST::Members::Attribute
                void_type_context_validator(member.type)
              when AST::Members::Mixin
                member.args.each do |arg|
                  no_self_type_validator(arg)
                  unless arg.is_a?(Types::Bases::Void)
                    void_type_context_validator(arg, true)
                  end
                end
                params =
                  if member.name.class?
                    module_decl = @env.normalized_module_entry(member.name) or raise
                    module_decl.type_params
                  else
                    interface_decl = @env.interface_decls.fetch(member.name)
                    interface_decl.decl.type_params
                  end
                InvalidTypeApplicationError.check!(type_name: member.name, params: params, args: member.args, location: member.location)
              when AST::Members::Var
                void_type_context_validator(member.type)
                if member.is_a?(AST::Members::ClassVariable)
                  no_self_type_validator(member.type)
                end
              end
            end
          end
        rescue BaseError => error
          @errors.add(error)
        end
      end

      def validate_class_module_alias_definition
        @env.class_alias_decls.each do |name, entry|
          RBS.logger.info "Validating class/module alias definition: `#{name}`..."
          @validator.validate_class_alias(entry: entry)
        rescue BaseError => error
          @errors.add error
        end
      end

      def validate_interface
        @env.interface_decls.each do |name, decl|
          RBS.logger.info "Validating interface: `#{name}`..."
          @builder.build_interface(name).each_type do |type|
            @validator.validate_type type, context: nil
          end

          @validator.validate_type_params(
            decl.decl.type_params,
            type_name: name,
            location: decl.decl.location&.aref(:type_params)
          )

          decl.decl.type_params.each do |param|
            if ub = param.upper_bound_type
              void_type_context_validator(ub)
              no_self_type_validator(ub)
              no_classish_type_validator(ub)
              @validator.validate_type(ub, context: nil)
            end

            if dt = param.default_type
              void_type_context_validator(dt, true)
              no_self_type_validator(dt)
              no_classish_type_validator(dt)
              @validator.validate_type(dt, context: nil)
            end
          end

          TypeParamDefaultReferenceError.check!(decl.decl.type_params)

          decl.decl.members.each do |member|
            case member
            when AST::Members::MethodDefinition
              @validator.validate_method_definition(member, type_name: name)
              member.overloads.each do |ov|
                void_type_context_validator(ov.method_type)
                no_classish_type_validator(ov.method_type)
              end
            end
          end
        rescue BaseError => error
          @errors.add(error)
        end
      end

      def validate_constant
        @env.constant_decls.each do |name, const|
          RBS.logger.info "Validating constant: `#{name}`..."
          @validator.validate_type const.decl.type, context: const.context
          @builder.ensure_namespace!(name.namespace, location: const.decl.location)
          no_self_type_validator(const.decl.type)
          no_classish_type_validator(const.decl.type)
          void_type_context_validator(const.decl.type)
        rescue BaseError => error
          @errors.add(error)
        end
      end

      def validate_global
        @env.global_decls.each do |name, global|
          RBS.logger.info "Validating global: `#{name}`..."
          @validator.validate_type global.decl.type, context: nil
          no_self_type_validator(global.decl.type)
          no_classish_type_validator(global.decl.type)
          void_type_context_validator(global.decl.type)
        rescue BaseError => error
          @errors.add(error)
        end
      end

      def validate_type_alias
        @env.type_alias_decls.each do |name, decl|
          RBS.logger.info "Validating alias: `#{name}`..."
          @builder.expand_alias1(name).tap do |type|
            @validator.validate_type type, context: nil
          end

          @validator.validate_type_alias(entry: decl)

          @validator.validate_type_params(
            decl.decl.type_params,
            type_name: name,
            location: decl.decl.location&.aref(:type_params)
          )

          decl.decl.type_params.each do |param|
            if ub = param.upper_bound_type
              void_type_context_validator(ub)
              no_self_type_validator(ub)
              no_classish_type_validator(ub)
              @validator.validate_type(ub, context: nil)
            end

            if dt = param.default_type
              void_type_context_validator(dt, true)
              no_self_type_validator(dt)
              no_classish_type_validator(dt)
              @validator.validate_type(dt, context: nil)
            end
          end

          TypeParamDefaultReferenceError.check!(decl.decl.type_params)

          no_self_type_validator(decl.decl.type)
          no_classish_type_validator(decl.decl.type)
          void_type_context_validator(decl.decl.type)
        rescue BaseError => error
          @errors.add(error)
        end
      end

      private

      def no_self_type_validator(type)
        if type.has_self_type?
          @errors.add WillSyntaxError.new("`self` type is not allowed in this context", location: type.location)
        end
      end

      def no_classish_type_validator(type)
        if type.has_classish_type?
          @errors.add WillSyntaxError.new("`instance` or `class` type is not allowed in this context", location: type.location)
        end
      end

      def void_type_context_validator(type, allowed_here = false)
        if allowed_here
          return if type.is_a?(Types::Bases::Void)
        end
        if type.with_nonreturn_void?
          @errors.add WillSyntaxError.new("`void` type is only allowed in return type or generics parameter", location: type.location)
        end
      end
    end
  end
end
