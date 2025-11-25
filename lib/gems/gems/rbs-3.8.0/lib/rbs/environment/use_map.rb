# frozen_string_literal: true

module RBS
  class Environment
    class UseMap
      class Table
        attr_reader :known_types, :children

        def initialize
          @known_types = Set[]
          @children = {}
        end

        def compute_children
          children.clear

          known_types.each do |type|
            unless type.namespace.empty?
              children[type.namespace] ||= Set[]
              children[type.namespace] << type
            end
          end

          self
        end
      end

      attr_reader :use_dirs

      def initialize(table:)
        @use_dirs = []
        @map = {}
        @table = table
      end

      def build_map(clause)
        case clause
        when AST::Directives::Use::SingleClause
          if clause.new_name
            @map[clause.new_name] = clause.type_name.absolute!
          else
            @map[clause.type_name.name] = clause.type_name.absolute!
          end
        when AST::Directives::Use::WildcardClause
          @table.children.fetch(clause.namespace.absolute!).each do |child|
            @map[child.name] = child
          end
        end

        self
      end

      def resolve?(type_name)
        return if type_name.absolute?

        hd, *tl = type_name.namespace.path

        if hd
          # namespace is not empty
          if tn = @map[hd]
            path = [*tn.namespace.path, tn.name, *tl]
            TypeName.new(
              namespace: Namespace.new(absolute: true, path: path),
              name: type_name.name
            )
          end
        else
          @map[type_name.name]
        end
      end

      def resolve(type_name)
        resolve?(type_name) || type_name
      end
    end
  end
end
