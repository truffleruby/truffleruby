# frozen_string_literal: true

module RBS
  module Resolver
    class TypeNameResolver
      attr_reader :all_names
      attr_reader :cache
      attr_reader :env

      def initialize(env)
        @all_names = Set[]
        @cache = {}
        @env = env

        all_names.merge(env.class_decls.keys)
        all_names.merge(env.interface_decls.keys)
        all_names.merge(env.type_alias_decls.keys)
        all_names.merge(env.class_alias_decls.keys)
      end

      def try_cache(query)
        cache.fetch(query) do
          result = yield
          cache[query] = result
        end
      end

      def resolve(type_name, context:)
        if type_name.absolute?
          return type_name
        end

        try_cache([type_name, context]) do
          head, tail = partition(type_name)

          head = resolve_in(head, context)

          if head
            if tail
              absolute_name = tail.with_prefix(head.to_namespace)
              if env.normalize_type_name?(absolute_name)
                absolute_name
              end
            else
              head
            end
          end
        end
      end

      def partition(type_name)
        if type_name.namespace.empty?
          head = type_name.name
          tail = nil
        else
          head, *tail = type_name.namespace.path

          head or raise

          tail = TypeName.new(
            name: type_name.name,
            namespace: Namespace.new(absolute: false, path: tail)
          )
        end

        [head, tail]
      end

      def resolve_in(head, context)
        if context
          parent, child = context
          case child
          when false
            resolve_in(head, parent)
          when TypeName
            name = TypeName.new(name: head, namespace: child.to_namespace)
            has_name?(name) || resolve_in(head, parent)
          end
        else
          has_name?(TypeName.new(name: head, namespace: Namespace.root))
        end
      end

      def has_name?(full_name)
        if all_names.include?(full_name)
          full_name
        end
      end
    end
  end
end
