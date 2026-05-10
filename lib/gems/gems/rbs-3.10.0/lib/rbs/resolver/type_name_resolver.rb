# frozen_string_literal: true

module RBS
  module Resolver
    class TypeNameResolver
      attr_reader :all_names
      attr_reader :cache
      attr_reader :aliases

      def initialize(all_names, aliases)
        @all_names = all_names
        @aliases = aliases
        @cache = {}
      end

      def self.new(*args)
        if args.size == 1 && args[0].is_a?(Environment)
          build(args[0])
        else
          super
        end
      end

      def self.build(env)
        all_names = Set.new #: Set[TypeName]

        all_names.merge(env.class_decls.keys)
        all_names.merge(env.interface_decls.keys)
        all_names.merge(env.type_alias_decls.keys)

        aliases = {} #: Hash[TypeName, [TypeName, context]]

        env.class_alias_decls.each do |name, entry|
          aliases[name] = [entry.decl.old_name, entry.context]
        end

        new(all_names, aliases)
      end

      def try_cache(query)
        cache.fetch(query) do
          result = yield
          cache[query] = result
        end
      end

      def resolve(type_name, context:)
        if type_name.absolute? && has_type_name?(type_name)
          return type_name
        end

        try_cache([type_name, context]) do
          if type_name.class?
            resolve_namespace0(type_name, context, Set.new) || nil
          else
            namespace = type_name.namespace

            if namespace.empty?
              resolve_type_name(type_name.name, context)
            else
              if namespace = resolve_namespace0(namespace.to_type_name, context, Set.new)
                type_name = TypeName.new(name: type_name.name, namespace: namespace.to_namespace)
                has_type_name?(type_name)
              end
            end
          end
        end
      end

      def resolve_namespace(type_name, context:)
        if type_name.absolute? && has_type_name?(type_name)
          return type_name
        end

        unless type_name.class?
          raise "Type name must be a class name: #{type_name}"
        end

        try_cache([type_name, context]) do
          ns = resolve_namespace0(type_name, context, Set.new) or return ns
        end
      end

      def has_type_name?(full_name)
        if all_names.include?(full_name)
          full_name
        end
      end

      def aliased_name?(type_name)
        if aliases.key?(type_name)
          type_name
        end
      end

      def resolve_type_name(type_name, context)
        if context
          outer, inner = context
          case inner
          when false
            resolve_type_name(type_name, outer)
          else
            has_type_name?(inner) or raise "Context must be normalized: #{inner.inspect}"
            has_type_name?(TypeName.new(name: type_name, namespace: inner.to_namespace)) || resolve_type_name(type_name, outer)
          end
        else
          type_name = TypeName.new(name: type_name, namespace: Namespace.root)
          has_type_name?(type_name)
        end
      end

      def resolve_head_namespace(head, context)
        if context
          outer, inner = context
          case inner
          when false
            resolve_head_namespace(head, outer)
          when TypeName
            has_type_name?(inner) or raise "Context must be normalized: #{inner.inspect}"
            type_name = TypeName.new(name: head, namespace: inner.to_namespace)
            has_type_name?(type_name) || aliased_name?(type_name) || resolve_head_namespace(head, outer)
          end
        else
          type_name = TypeName.new(name: head, namespace: Namespace.root)
          has_type_name?(type_name) || aliased_name?(type_name)
        end
      end

      def normalize_namespace(type_name, rhs, context, visited)
        if visited.include?(type_name)
          # Cycle detected
          return false
        end

        visited << type_name

        begin
          resolve_namespace0(rhs, context, visited)
        ensure
          visited.delete(type_name)
        end
      end

      def resolve_namespace0(type_name, context, visited)
        head, *tail = [*type_name.namespace.path, type_name.name]

        head = head #: Symbol

        head =
          if type_name.absolute?
            root_name = TypeName.new(name: head, namespace: Namespace.root)
            has_type_name?(root_name) || aliased_name?(root_name)
          else
            resolve_head_namespace(head, context)
          end

        if head
          if (rhs, context = aliases.fetch(head, nil))
            head = normalize_namespace(head, rhs, context, visited) or return head
          end

          tail.inject(head) do |namespace, name|
            type_name = TypeName.new(name: name, namespace: namespace.to_namespace)
            case
            when has_type_name?(type_name)
              type_name
            when (rhs, context = aliases.fetch(type_name, nil))
              m = normalize_namespace(type_name, rhs, context, visited) or return m
            else
              return nil
            end
          end
        end
      end
    end
  end
end
